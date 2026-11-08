package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.shared.domain.ports.out.TracingPort;
import com.food4good.storage.domain.model.MarketJob;
import com.food4good.storage.domain.model.StorageCommand;
import com.food4good.storage.domain.ports.in.IngredientRequest;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
class StorageCommandRepositoryAdapter implements StorageCommandRepositoryPort {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;
    private final JsonMapper json;
    private final TracingPort tracing;
    private final Counter failed;

    StorageCommandRepositoryAdapter(
            JdbcClient jdbc, JdbcTemplate batch, JsonMapper json, TracingPort tracing, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.batch = batch;
        this.json = json;
        this.tracing = tracing;
        this.failed = Counter.builder("jobs.failed")
                .description("Work that gave up after repeated failures")
                .tag("queue", "storage")
                .register(meters);
    }

    @Override
    public void enqueueRequests(List<IngredientRequest> requests) {
        var payload = requests.stream()
                .map(request -> Map.of(
                        "orderId", request.orderId().toString(),
                        "quantities", quantitiesById(request)))
                .toList();
        jdbc.sql("""
                        INSERT INTO storage_command (type, payload, dedupe_key, traceparent)
                        VALUES ('REQUEST_INGREDIENTS', :payload::jsonb, :dedupeKey, :traceparent)
                        """)
                .param("payload", json.writeValueAsString(Map.of("requests", payload)))
                .param("dedupeKey", "REQUEST_INGREDIENTS:" + requests.getFirst().orderId())
                .param("traceparent", tracing.currentContext())
                .update();
        jdbc.sql("NOTIFY storage_commands").update();
    }

    private static Map<String, Integer> quantitiesById(IngredientRequest request) {
        var quantities = new HashMap<String, Integer>();
        request.quantities().forEach((ingredient, quantity) -> quantities.put(ingredient.id(), quantity));
        return quantities;
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
        var cause = tracing.currentContext();
        batch.batchUpdate(
                """
                INSERT INTO storage_command (type, payload, dedupe_key, traceparent)
                VALUES (?, jsonb_build_object('orderId', ?::text), ?, ?)
                """,
                orderIds.stream()
                        .map(orderId -> new Object[] {type, orderId.toString(), type + ":" + orderId, cause})
                        .toList());
        jdbc.sql("NOTIFY storage_commands").update();
    }

    @Override
    public void enqueuePurchase(MarketJob job, int quantity) {
        jdbc.sql("""
                        INSERT INTO storage_command (type, payload, dedupe_key, traceparent)
                        VALUES ('ADD_PURCHASE',
                                jsonb_build_object(
                                    'orderId', :orderId::text,
                                    'marketJobId', :marketJobId::bigint,
                                    'attempt', :attempt::int,
                                    'ingredient', :ingredient::text,
                                    'quantity', :quantity::int),
                                :dedupeKey, :traceparent)
                        """)
                .param("orderId", job.orderId().toString())
                .param("marketJobId", job.id())
                .param("attempt", job.attempt())
                .param("ingredient", job.ingredient().id())
                .param("quantity", quantity)
                .param("dedupeKey", "ADD_PURCHASE:%d:%d".formatted(job.id(), job.attempt()))
                .param("traceparent", tracing.currentContext())
                .update();
        jdbc.sql("NOTIFY storage_commands").update();
    }

    @Override
    public int countPending() {
        return jdbc.sql("SELECT count(*) FROM storage_command WHERE status = 'PENDING'").query(Integer.class).single();
    }

    @Override
    public List<StorageCommand> findPending(int limit) {
        return jdbc.sql("""
                        SELECT id, type, payload, traceparent
                        FROM storage_command
                        WHERE status = 'PENDING'
                        ORDER BY id
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((row, rowNumber) -> toCommand(
                        row.getLong("id"), row.getString("type"), row.getString("payload"), row.getString("traceparent")))
                .list();
    }

    @Override
    public void markDone(long commandId) {
        jdbc.sql("UPDATE storage_command SET status = 'DONE' WHERE id = :id").param("id", commandId).update();
    }

    @Override
    public boolean recordFailure(long commandId, String error, int maxAttempts) {
        boolean gaveUp = jdbc.sql("""
                        UPDATE storage_command
                        SET attempts = attempts + 1,
                            last_error = :error,
                            status = CASE WHEN attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE status END
                        WHERE id = :id
                        RETURNING status = 'FAILED'
                        """)
                .param("error", error)
                .param("maxAttempts", maxAttempts)
                .param("id", commandId)
                .query(Boolean.class)
                .single();
        if (gaveUp) {
            failed.increment();
        }
        return gaveUp;
    }

    @Override
    public void enqueueRepair(UUID orderId, IngredientRequest requestOrNull, int repair) {
        var dedupeKey = "REPAIR:%s:%d".formatted(orderId, repair);
        if (requestOrNull == null) {
            jdbc.sql("""
                            INSERT INTO storage_command (type, payload, dedupe_key, traceparent)
                            VALUES ('RESERVE', jsonb_build_object('orderId', :orderId::text), :dedupeKey, :traceparent)
                            """)
                    .param("orderId", orderId.toString())
                    .param("dedupeKey", dedupeKey)
                    .param("traceparent", tracing.currentContext())
                    .update();
        } else {
            jdbc.sql("""
                            INSERT INTO storage_command (type, payload, dedupe_key, traceparent)
                            VALUES ('REQUEST_INGREDIENTS', :payload::jsonb, :dedupeKey, :traceparent)
                            """)
                    .param("payload", json.writeValueAsString(Map.of("requests", List.of(Map.of(
                            "orderId", orderId.toString(), "quantities", quantitiesById(requestOrNull))))))
                    .param("dedupeKey", dedupeKey)
                    .param("traceparent", tracing.currentContext())
                    .update();
        }
        jdbc.sql("NOTIFY storage_commands").update();
    }

    @Override
    public Optional<IngredientRequest> findRequest(UUID orderId) {
        return jdbc.sql("""
                        SELECT payload FROM storage_command
                        WHERE type = 'REQUEST_INGREDIENTS'
                          AND payload -> 'requests' @> jsonb_build_array(jsonb_build_object('orderId', :orderId::text))
                        ORDER BY id
                        LIMIT 1
                        """)
                .param("orderId", orderId.toString())
                .query(String.class)
                .optional()
                .flatMap(payload -> toRequests(json.readTree(payload)).stream()
                        .filter(request -> request.orderId().equals(orderId))
                        .findFirst());
    }

    private StorageCommand toCommand(long id, String type, String payload, String cause) {
        var fields = json.readTree(payload);
        return switch (type) {
            case "REQUEST_INGREDIENTS" -> new StorageCommand.RequestIngredients(id, toRequests(fields), cause);
            case "RESERVE" -> new StorageCommand.Reserve(id, orderId(fields), cause);
            case "CONSUME" -> new StorageCommand.Consume(id, orderId(fields), cause);
            case "ADD_PURCHASE" -> new StorageCommand.AddPurchase(
                    id,
                    fields.get("marketJobId").asLong(),
                    fields.get("attempt").asInt(),
                    orderId(fields),
                    Ingredient.fromId(fields.get("ingredient").asString()),
                    fields.get("quantity").asInt(),
                    cause);
            default -> throw new IllegalStateException("Unknown storage command type " + type);
        };
    }

    private static UUID orderId(JsonNode fields) {
        return UUID.fromString(fields.get("orderId").asString());
    }

    private static List<IngredientRequest> toRequests(JsonNode fields) {
        var requests = new ArrayList<IngredientRequest>();
        for (var request : fields.get("requests")) {
            var quantities = new HashMap<Ingredient, Integer>();
            request.get("quantities").properties().forEach(
                    entry -> quantities.put(Ingredient.fromId(entry.getKey()), entry.getValue().asInt()));
            requests.add(new IngredientRequest(orderId(request), quantities));
        }
        return requests;
    }
}
