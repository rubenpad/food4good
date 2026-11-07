package com.food4good;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.shared.infrastructure.adapters.in.rest.LiveChannel;
import com.food4good.shared.infrastructure.adapters.in.worker.SingleInstanceWorker;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * Base for behaviour tests: the whole app against real PostgreSQL, driven and observed only
 * through the public HTTP API. Tests control behaviour through configuration, never test hooks.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private List<SingleInstanceWorker> workers;

    @Autowired
    private List<LiveChannel> liveChannels;

    @Autowired
    private Flyway flyway;

    @Autowired
    private HikariDataSource dataSource;

    /** Setup only: for states the API can't produce, such as a defect. Assertions stay on the API. */
    @Autowired
    protected JdbcTemplate database;

    /**
     * Gives every test what a new database has, without the cost of a new container and app. The
     * workers stop first, which also waits for market calls in progress, so nothing from the last
     * test can land in this one.
     */
    @BeforeEach
    void startFromANewDatabase() {
        try (var stopping = Executors.newVirtualThreadPerTaskExecutor()) {
            workers.forEach(worker -> stopping.execute(worker::stop));
        }
        flyway.clean();
        flyway.migrate();
        // Pooled connections hold prepared statements for the tables that were just replaced.
        dataSource.getHikariPoolMXBean().softEvictConnections();
        liveChannels.forEach(LiveChannel::discard);
        workers.forEach(SingleInstanceWorker::start);
    }

    protected RestClient api() {
        return RestClient.create("http://localhost:" + port + "/api");
    }

    /**
     * Simulates a defect: from now on, writing to {@code table} fails whenever {@code condition} holds
     * for the new row (SQL using NEW and OLD), until {@link #stopFailing} is called.
     */
    protected void failWrites(String table, String operation, String condition) {
        database.execute("""
                CREATE OR REPLACE FUNCTION fail_on_purpose() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'failing on purpose'; END $$ LANGUAGE plpgsql
                """);
        database.execute("CREATE TRIGGER fail_on_purpose BEFORE %s ON %s FOR EACH ROW WHEN (%s) EXECUTE FUNCTION fail_on_purpose()"
                .formatted(operation, table, condition));
    }

    protected void stopFailing(String table) {
        database.execute("DROP TRIGGER fail_on_purpose ON " + table);
    }

    protected LiveEvents subscribeToEvents() throws Exception {
        return LiveEvents.subscribe("http://localhost:" + port + "/api");
    }

    protected DocumentContext getJson(String path) {
        return JsonPath.parse(api().get().uri(path).retrieve().body(String.class));
    }

    /** Sends a JSON body, given as a raw string or as anything serializable, and returns any response as is. */
    protected ResponseEntity<String> send(HttpMethod method, String path, Object body) {
        return api().method(method)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, response) -> {
                })
                .toEntity(String.class);
    }

    /** Places Orders and returns the accepted Orders' ids. */
    protected List<String> placeOrders(int quantity) {
        var response = send(HttpMethod.POST, "/orders", Map.of("quantity", quantity));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JsonPath.read(response.getBody(), "$.orderIds");
    }

    /** The Order as the Order history reports it. */
    protected Map<String, Object> order(String id) {
        List<Map<String, Object>> found = getJson("/orders?limit=200").read("$.items[?(@.id == '" + id + "')]");
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    protected String statusOf(String orderId) {
        return (String) order(orderId).get("status");
    }

    /** One Ingredient's stock as {@code [onHand, reserved, available]}. */
    protected List<Integer> stockOf(String ingredient) {
        List<Map<String, Object>> stock = getJson("/storage").read("$[?(@.ingredient == '" + ingredient + "')]");
        assertThat(stock).hasSize(1);
        var row = stock.getFirst();
        return List.of((Integer) row.get("onHand"), (Integer) row.get("reserved"), (Integer) row.get("available"));
    }

    protected void assertStockRulesHold() {
        List<Map<String, Integer>> stock = getJson("/storage").read("$");
        assertThat(stock).hasSize(10).allSatisfy(row -> {
            assertThat(row.get("onHand")).isNotNegative();
            assertThat(row.get("reserved")).isNotNegative().isLessThanOrEqualTo(row.get("onHand"));
            assertThat(row.get("available")).isEqualTo(row.get("onHand") - row.get("reserved"));
        });
    }

    /**
     * Edits every Recipe to need the same Ingredients and take the same time, so the random draw
     * doesn't matter.
     */
    protected void givenEveryRecipeIs(Map<String, Integer> ingredients, int preparationTimeSeconds) {
        var items = ingredients.entrySet().stream()
                .map(entry -> Map.of("ingredient", entry.getKey(), "quantity", entry.getValue()))
                .toList();
        List<Map<String, Object>> recipes = getJson("/recipes").read("$");
        recipes.forEach(recipe -> api().put()
                .uri("/recipes/{id}", recipe.get("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", recipe.get("name"),
                        "preparationTimeSeconds", preparationTimeSeconds,
                        "ingredients", items))
                .retrieve()
                .toBodilessEntity());
    }
}
