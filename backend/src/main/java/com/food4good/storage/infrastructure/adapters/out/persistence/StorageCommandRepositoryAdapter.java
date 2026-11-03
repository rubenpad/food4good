package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.model.MarketJob;
import com.food4good.storage.domain.model.StorageCommand;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class StorageCommandRepositoryAdapter implements StorageCommandRepositoryPort {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;
    private final JsonMapper json;

    StorageCommandRepositoryAdapter(JdbcClient jdbc, JdbcTemplate batch, JsonMapper json) {
        this.jdbc = jdbc;
        this.batch = batch;
        this.json = json;
    }

    @Override
    public void enqueueReservations(List<UUID> orderIds) {
        enqueue("RESERVE", orderIds);
    }

    @Override
    public void enqueueConsumptions(List<UUID> orderIds) {
        enqueue("CONSUME", orderIds);
    }

    private void enqueue(String type, List<UUID> orderIds) {
        batch.batchUpdate(
                """
                INSERT INTO storage_command (type, payload, dedupe_key)
                VALUES (?, jsonb_build_object('orderId', ?::text), ?)
                """,
                orderIds.stream()
                        .map(orderId -> new Object[] {type, orderId.toString(), type + ":" + orderId})
                        .toList());
        jdbc.sql("NOTIFY storage_commands").update();
    }

    @Override
    public void enqueuePurchase(MarketJob job, int quantity) {
        jdbc.sql("""
                        INSERT INTO storage_command (type, payload, dedupe_key)
                        VALUES ('ADD_PURCHASE',
                                jsonb_build_object(
                                    'orderId', :orderId::text,
                                    'marketJobId', :marketJobId::bigint,
                                    'attempt', :attempt::int,
                                    'ingredient', :ingredient::text,
                                    'quantity', :quantity::int),
                                :dedupeKey)
                        """)
                .param("orderId", job.orderId().toString())
                .param("marketJobId", job.id())
                .param("attempt", job.attempt())
                .param("ingredient", job.ingredient().id())
                .param("quantity", quantity)
                .param("dedupeKey", "ADD_PURCHASE:%d:%d".formatted(job.id(), job.attempt()))
                .update();
        jdbc.sql("NOTIFY storage_commands").update();
    }

    @Override
    public List<StorageCommand> findPending(int limit) {
        return jdbc.sql("""
                        SELECT id, type, payload
                        FROM storage_command
                        WHERE status = 'PENDING'
                        ORDER BY id
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((row, rowNumber) -> toCommand(row.getLong("id"), row.getString("type"), row.getString("payload")))
                .list();
    }

    @Override
    public void markDone(long commandId) {
        jdbc.sql("UPDATE storage_command SET status = 'DONE' WHERE id = :id").param("id", commandId).update();
    }

    @Override
    public void recordFailure(long commandId, String error, int maxAttempts) {
        jdbc.sql("""
                        UPDATE storage_command
                        SET attempts = attempts + 1,
                            last_error = :error,
                            status = CASE WHEN attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE status END
                        WHERE id = :id
                        """)
                .param("error", error)
                .param("maxAttempts", maxAttempts)
                .param("id", commandId)
                .update();
    }

    private StorageCommand toCommand(long id, String type, String payload) {
        var fields = json.readTree(payload);
        var orderId = UUID.fromString(fields.get("orderId").asString());
        return switch (type) {
            case "RESERVE" -> new StorageCommand.Reserve(id, orderId);
            case "CONSUME" -> new StorageCommand.Consume(id, orderId);
            case "ADD_PURCHASE" -> new StorageCommand.AddPurchase(
                    id,
                    fields.get("marketJobId").asLong(),
                    fields.get("attempt").asInt(),
                    orderId,
                    Ingredient.fromId(fields.get("ingredient").asString()),
                    fields.get("quantity").asInt());
            default -> throw new IllegalStateException("Unknown storage command type " + type);
        };
    }
}
