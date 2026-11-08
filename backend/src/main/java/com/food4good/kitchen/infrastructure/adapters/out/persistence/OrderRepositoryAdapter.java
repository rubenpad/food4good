package com.food4good.kitchen.infrastructure.adapters.out.persistence;

import com.food4good.kitchen.domain.model.NewOrder;
import com.food4good.kitchen.domain.model.Order;
import com.food4good.kitchen.domain.model.OrderQuery;
import com.food4good.kitchen.domain.model.OrderStatus;
import com.food4good.kitchen.domain.model.TracedOrder;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepositoryAdapter implements OrderRepositoryPort {

    private static final String SELECT_ORDERS = """
            SELECT id, recipe_id, recipe_name, preparation_ms, status,
                   accepted_at, ready_at, preparing_at, prepare_until, delivered_at
            FROM orders
            WHERE true
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    OrderRepositoryAdapter(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    /** One statement for all of them, which is much cheaper than a batch when a burst arrives. */
    @Override
    public void insertAll(List<NewOrder> orders) {
        batch.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO orders (id, recipe_id, recipe_name, preparation_ms, traceparent)
                    SELECT * FROM unnest(?::uuid[], ?::uuid[], ?::text[], ?::bigint[], ?::text[])
                    """);
            statement.setArray(1, connection.createArrayOf(
                    "uuid", orders.stream().map(NewOrder::id).toArray()));
            statement.setArray(2, connection.createArrayOf(
                    "uuid", orders.stream().map(order -> order.recipe().id()).toArray()));
            statement.setArray(3, connection.createArrayOf(
                    "text", orders.stream().map(order -> order.recipe().name()).toArray()));
            statement.setArray(4, connection.createArrayOf(
                    "bigint", orders.stream().map(order -> order.preparationTime().toMillis()).toArray()));
            statement.setArray(5, connection.createArrayOf(
                    "text", orders.stream().map(NewOrder::cause).toArray()));
            return statement;
        });
    }

    @Override
    public void lockAdmission() {
        jdbc.sql("SELECT pg_advisory_xact_lock(4004010)").query().listOfRows();
    }

    @Override
    public Duration backlogWork() {
        // clock_timestamp, not now: now is when the transaction began, which may be before it got the lock.
        var millis = jdbc.sql("""
                        SELECT coalesce(sum(CASE
                            WHEN status = 'PREPARING'
                                THEN greatest(0, ceil(extract(epoch FROM prepare_until - clock_timestamp()) * 1000))
                            ELSE preparation_ms
                        END), 0)::bigint
                        FROM orders
                        WHERE status IN ('WAITING_FOR_INGREDIENTS', 'READY', 'PREPARING')
                        """)
                .query(Long.class)
                .single();
        return Duration.ofMillis(millis);
    }

    @Override
    public List<Order> findByIds(Collection<UUID> ids) {
        return jdbc.sql(SELECT_ORDERS + " AND id = ANY (:ids)")
                .param("ids", ids.toArray(UUID[]::new))
                .query((row, rowNumber) -> toOrder(row))
                .list();
    }

    @Override
    public List<Order> find(OrderQuery query) {
        var sql = new StringBuilder(SELECT_ORDERS);
        if (query.status() != null) {
            sql.append(" AND status = :status");
        }
        if (query.cursor() != null) {
            sql.append(" AND (accepted_at, id) < (:cursorAcceptedAt, :cursorId)");
        }
        sql.append(" ORDER BY accepted_at DESC, id DESC LIMIT :fetch");

        var statement = jdbc.sql(sql.toString()).param("fetch", query.limit() + 1);
        if (query.status() != null) {
            statement = statement.param("status", query.status().name());
        }
        if (query.cursor() != null) {
            statement = statement
                    .param("cursorAcceptedAt", OffsetDateTime.ofInstant(query.cursor().acceptedAt(), ZoneOffset.UTC))
                    .param("cursorId", query.cursor().id());
        }
        return statement.query((row, rowNumber) -> toOrder(row)).list();
    }

    @Override
    public void markReady(UUID orderId) {
        jdbc.sql("UPDATE orders SET status = 'READY', ready_at = now() WHERE id = :id AND status = 'WAITING_FOR_INGREDIENTS'")
                .param("id", orderId)
                .update();
        // Sent on commit, so the scheduler only wakes once the Order really is ready.
        jdbc.sql("NOTIFY kitchen_orders").update();
    }

    @Override
    public List<TracedOrder> deliverDue() {
        return jdbc.sql("""
                        UPDATE orders
                        SET status = 'DELIVERED', delivered_at = now()
                        WHERE status = 'PREPARING' AND prepare_until <= now()
                        RETURNING id, traceparent
                        """)
                .query(OrderRepositoryAdapter::toTracedOrder)
                .list();
    }

    @Override
    public int countPreparing() {
        return jdbc.sql("SELECT count(*) FROM orders WHERE status = 'PREPARING'").query(Integer.class).single();
    }

    @Override
    public List<TracedOrder> startPreparing(int limit) {
        return jdbc.sql("""
                        UPDATE orders
                        SET status = 'PREPARING',
                            preparing_at = now(),
                            prepare_until = now() + preparation_ms * interval '1 millisecond'
                        WHERE id IN (
                            SELECT id FROM orders
                            WHERE status = 'READY'
                            ORDER BY ready_at, id
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
                        RETURNING id, traceparent
                        """)
                .param("limit", limit)
                .query(OrderRepositoryAdapter::toTracedOrder)
                .list();
    }

    private static TracedOrder toTracedOrder(ResultSet row, int rowNumber) throws SQLException {
        return new TracedOrder(row.getObject("id", UUID.class), row.getString("traceparent"));
    }

    @Override
    public Optional<Duration> timeUntilNextDishIsDue() {
        // Measured by the database's clock, which is the one that set the due times.
        return jdbc.sql("""
                        SELECT greatest(0, ceil(extract(epoch FROM min(prepare_until) - clock_timestamp()) * 1000))::bigint
                        FROM orders
                        WHERE status = 'PREPARING'
                        """)
                .query((row, rowNumber) -> {
                    var millis = row.getLong(1);
                    return row.wasNull() ? Optional.<Duration>empty() : Optional.of(Duration.ofMillis(millis));
                })
                .single();
    }

    @Override
    public Map<OrderStatus, Integer> countOpenByStatus() {
        var counts = new EnumMap<OrderStatus, Integer>(OrderStatus.class);
        jdbc.sql("SELECT status, count(*) AS orders FROM orders WHERE status IN ('WAITING_FOR_INGREDIENTS', 'READY', 'PREPARING') GROUP BY status")
                .query(row -> {
                    counts.put(OrderStatus.valueOf(row.getString("status")), row.getInt("orders"));
                });
        return counts;
    }

    private static Order toOrder(ResultSet row) throws SQLException {
        return new Order(
                row.getObject("id", UUID.class),
                row.getObject("recipe_id", UUID.class),
                row.getString("recipe_name"),
                Duration.ofMillis(row.getLong("preparation_ms")),
                OrderStatus.valueOf(row.getString("status")),
                instant(row, "accepted_at"),
                instant(row, "ready_at"),
                instant(row, "preparing_at"),
                instant(row, "prepare_until"),
                instant(row, "delivered_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
