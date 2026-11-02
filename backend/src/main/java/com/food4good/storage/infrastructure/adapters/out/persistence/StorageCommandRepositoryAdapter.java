package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.storage.domain.model.StorageCommand;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StorageCommandRepositoryAdapter implements StorageCommandRepositoryPort {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    StorageCommandRepositoryAdapter(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    @Override
    public void enqueueReservations(List<UUID> orderIds) {
        batch.batchUpdate(
                """
                INSERT INTO storage_command (type, payload, dedupe_key)
                VALUES ('RESERVE', jsonb_build_object('orderId', ?::text), ?)
                """,
                orderIds.stream()
                        .map(orderId -> new Object[] {orderId.toString(), "RESERVE:" + orderId})
                        .toList());
        jdbc.sql("NOTIFY storage_commands").update();
    }

    @Override
    public List<StorageCommand> findPending(int limit) {
        return jdbc.sql("""
                        SELECT id, type, payload ->> 'orderId' AS order_id
                        FROM storage_command
                        WHERE status = 'PENDING'
                        ORDER BY id
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((row, rowNumber) -> toCommand(row.getLong("id"), row.getString("type"), row.getString("order_id")))
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

    private static StorageCommand toCommand(long id, String type, String orderId) {
        return switch (type) {
            case "RESERVE" -> new StorageCommand.Reserve(id, UUID.fromString(orderId));
            default -> throw new IllegalStateException("Unknown storage command type " + type);
        };
    }
}
