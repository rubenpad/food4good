package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.shared.domain.ports.out.TracingPort;
import com.food4good.storage.domain.model.MarketJob;
import com.food4good.storage.domain.model.MarketShortfall;
import com.food4good.storage.domain.ports.out.MarketJobRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MarketJobRepositoryAdapter implements MarketJobRepositoryPort {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;
    private final TracingPort tracing;
    private final Counter failed;

    MarketJobRepositoryAdapter(JdbcClient jdbc, JdbcTemplate batch, TracingPort tracing, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.batch = batch;
        this.tracing = tracing;
        this.failed = Counter.builder("jobs.failed")
                .description("Work that gave up after repeated failures")
                .tag("queue", "market")
                .register(meters);
    }

    @Override
    public void insertAll(List<MarketShortfall> shortfalls) {
        var cause = tracing.currentContext();
        batch.batchUpdate(
                // A repair can bring back a job that finished or failed; a live job is left alone.
                """
                INSERT INTO market_job (order_id, ingredient, remaining, traceparent) VALUES (?, ?, ?, ?)
                ON CONFLICT (order_id, ingredient) DO UPDATE
                SET remaining = EXCLUDED.remaining, status = 'PENDING', zero_streak = 0, error_attempts = 0,
                    next_attempt_at = now(), lease_until = NULL, traceparent = EXCLUDED.traceparent
                WHERE market_job.status IN ('DONE', 'FAILED')
                """,
                shortfalls.stream()
                        .map(item -> new Object[] {item.orderId(), item.ingredient().id(), item.units(), cause})
                        .toList());
        notifyBuyer();
    }

    @Override
    public int countPending() {
        return jdbc.sql("SELECT count(*) FROM market_job WHERE status IN ('PENDING', 'IN_FLIGHT')")
                .query(Integer.class)
                .single();
    }

    @Override
    public List<MarketJob> claimDue(int limit, Duration lease) {
        return jdbc.sql("""
                        UPDATE market_job
                        SET status = 'IN_FLIGHT',
                            attempt = attempt + 1,
                            lease_until = now() + :leaseMillis * interval '1 millisecond'
                        WHERE id IN (
                            SELECT id FROM market_job
                            WHERE (status = 'PENDING' AND next_attempt_at <= now())
                               OR (status = 'IN_FLIGHT' AND lease_until <= now())
                            ORDER BY next_attempt_at
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        RETURNING id, order_id, ingredient, remaining, attempt, zero_streak, error_attempts, traceparent
                        """)
                .param("leaseMillis", lease.toMillis())
                .param("limit", limit)
                .query((row, rowNumber) -> new MarketJob(
                        row.getLong("id"),
                        row.getObject("order_id", UUID.class),
                        Ingredient.fromId(row.getString("ingredient")),
                        row.getInt("remaining"),
                        row.getInt("attempt"),
                        row.getInt("zero_streak"),
                        row.getInt("error_attempts"),
                        row.getString("traceparent")))
                .list();
    }

    @Override
    public boolean recordSale(MarketJob job, int sold) {
        var updated = jdbc.sql("""
                        UPDATE market_job
                        SET remaining = greatest(0, remaining - :sold),
                            status = CASE WHEN remaining - :sold <= 0 THEN 'DONE' ELSE 'PENDING' END,
                            zero_streak = 0,
                            next_attempt_at = now(),
                            lease_until = NULL
                        WHERE id = :id AND attempt = :attempt AND status = 'IN_FLIGHT'
                        """)
                .param("sold", sold)
                .param("id", job.id())
                .param("attempt", job.attempt())
                .update();
        notifyBuyer();
        return updated == 1;
    }

    @Override
    public void recordNothingSold(MarketJob job, Duration wait) {
        jdbc.sql("""
                        UPDATE market_job
                        SET status = 'PENDING',
                            zero_streak = zero_streak + 1,
                            next_attempt_at = now() + :waitMillis * interval '1 millisecond',
                            lease_until = NULL
                        WHERE id = :id AND attempt = :attempt AND status = 'IN_FLIGHT'
                        """)
                .param("waitMillis", wait.toMillis())
                .param("id", job.id())
                .param("attempt", job.attempt())
                .update();
        notifyBuyer();
    }

    @Override
    public void recordError(MarketJob job, String error, Duration wait, int maxAttempts) {
        var gaveUp = jdbc.sql("""
                        UPDATE market_job
                        SET error_attempts = error_attempts + 1,
                            last_error = :error,
                            status = CASE WHEN error_attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END,
                            next_attempt_at = now() + :waitMillis * interval '1 millisecond',
                            lease_until = NULL
                        WHERE id = :id AND attempt = :attempt AND status = 'IN_FLIGHT'
                        RETURNING status = 'FAILED'
                        """)
                .param("error", error)
                .param("maxAttempts", maxAttempts)
                .param("waitMillis", wait.toMillis())
                .param("id", job.id())
                .param("attempt", job.attempt())
                .query(Boolean.class)
                .optional()
                .orElse(false);
        if (gaveUp) {
            failed.increment();
        }
        notifyBuyer();
    }

    @Override
    public Optional<Duration> timeUntilNextDue() {
        return jdbc.sql("""
                        SELECT greatest(0, ceil(extract(epoch FROM min(next_attempt_at) - clock_timestamp()) * 1000))::bigint
                        FROM market_job
                        WHERE status = 'PENDING'
                        """)
                .query((row, rowNumber) -> {
                    var millis = row.getLong(1);
                    return row.wasNull() ? Optional.<Duration>empty() : Optional.of(Duration.ofMillis(millis));
                })
                .single();
    }

    /** Sent on commit, so the buyer wakes for new jobs and for call slots that have freed up. */
    private void notifyBuyer() {
        jdbc.sql("NOTIFY market_jobs").update();
    }
}
