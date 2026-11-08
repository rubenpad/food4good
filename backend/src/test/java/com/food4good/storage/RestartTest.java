package com.food4good.storage;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.Food4GoodApplication;
import com.food4good.TestcontainersConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The app stopped and started again against the same database, driven through its API. */
@ExtendWith(OutputCaptureExtension.class)
class RestartTest {

    private static final Duration PATIENCE = Duration.ofSeconds(45);
    private static final String BUY = "/api/farmers-market/buy";

    @RegisterExtension
    static final WireMockExtension market = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgres();
    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();

    @BeforeEach
    void startDatabase() {
        postgres.start();
        marketSells(0);
    }

    @AfterEach
    void stopEverything() {
        instances.forEach(ConfigurableApplicationContext::close);
        postgres.stop();
    }

    private ConfigurableApplicationContext start() {
        var instance = new SpringApplicationBuilder(Food4GoodApplication.class).run(
                "--server.port=0",
                "--food4good.kitchen.cooks=2",
                "--food4good.kitchen.preparation-time-scale=0.01",
                "--food4good.storage.reconcile-interval=500ms",
                "--food4good.market.url=" + market.baseUrl(),
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword());
        instances.add(instance);
        return instance;
    }

    private void stop(ConfigurableApplicationContext instance) {
        instance.close();
        instances.remove(instance);
    }

    private JdbcTemplate database() {
        return new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    private static RestClient api(ConfigurableApplicationContext instance) {
        return RestClient.create("http://localhost:" + instance.getEnvironment().getProperty("local.server.port") + "/api");
    }

    private void marketSells(int quantity) {
        market.stubFor(get(urlPathEqualTo(BUY)).willReturn(okJson("{\"quantitySold\": " + quantity + "}")));
    }

    private static void everyRecipeIs(RestClient api, Map<String, Integer> ingredients, int seconds) {
        var items = ingredients.entrySet().stream()
                .map(entry -> Map.of("ingredient", entry.getKey(), "quantity", entry.getValue()))
                .toList();
        List<Map<String, Object>> recipes = JsonPath.read(api.get().uri("/recipes").retrieve().body(String.class), "$");
        recipes.forEach(recipe -> api.put().uri("/recipes/{id}", recipe.get("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", recipe.get("name"), "preparationTimeSeconds", seconds, "ingredients", items))
                .retrieve().toBodilessEntity());
    }

    private static List<String> placeOrders(RestClient api, int quantity) {
        var body = api.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity)).retrieve().body(String.class);
        return JsonPath.read(body, "$.orderIds");
    }

    private static List<Map<String, Object>> orders(RestClient api) {
        return JsonPath.read(api.get().uri("/orders?limit=200").retrieve().body(String.class), "$.items");
    }

    private static Map<String, Object> order(RestClient api, String id) {
        return orders(api).stream().filter(order -> order.get("id").equals(id)).findFirst().orElseThrow();
    }

    private static List<Map<String, Object>> purchases(RestClient api) {
        return JsonPath.read(api.get().uri("/purchases?limit=200").retrieve().body(String.class), "$.items");
    }

    private static void awaitAllDelivered(RestClient api, List<String> ids) {
        Awaitility.await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                ids.forEach(id -> assertThat(order(api, id).get("status")).isEqualTo("DELIVERED")));
    }

    @Test
    void ordersWaitingForIngredientsAreDeliveredExactlyOnceAfterARestart() {
        var before = start();
        everyRecipeIs(api(before), Map.of("meat", 3), 100);
        var ids = placeOrders(api(before), 10);
        Awaitility.await().atMost(PATIENCE).until(() -> !market.getAllServeEvents().isEmpty());
        stop(before);

        marketSells(3);
        var api = api(start());

        awaitAllDelivered(api, ids);
        assertThat(orders(api)).hasSize(10).extracting(order -> order.get("id")).doesNotHaveDuplicates();
        var bought = purchases(api).stream().mapToInt(purchase -> (Integer) purchase.get("quantity")).sum();
        Map<String, Integer> meat = JsonPath.<List<Map<String, Integer>>>read(
                api.get().uri("/storage").retrieve().body(String.class), "$[?(@.ingredient == 'meat')]").getFirst();
        // Every unit bought is either used by exactly one Dish or still on hand.
        assertThat(meat.get("onHand")).isEqualTo(5 + bought - 30);
        assertThat(meat.get("reserved")).isZero();
    }

    @Test
    void aMarketCallInFlightWhenAnInstanceDiedIsTakenOverOnceItsLeaseRunsOut() {
        var before = start();
        everyRecipeIs(api(before), Map.of("cheese", 6), 100);
        var id = placeOrders(api(before), 1).getFirst();
        Awaitility.await().atMost(PATIENCE).until(() -> !market.getAllServeEvents().isEmpty());
        stop(before);
        // As if the call were still in progress on an instance that died: leased for 4 more seconds.
        database().update("UPDATE market_job SET status = 'IN_FLIGHT', lease_until = now() + interval '4 seconds'");
        market.resetRequests();
        marketSells(1);

        var started = Instant.now();
        var api = api(start());

        Awaitility.await().atMost(PATIENCE).until(() -> !market.getAllServeEvents().isEmpty());
        assertThat(Duration.between(started, Instant.now())).isGreaterThanOrEqualTo(Duration.ofSeconds(3));
        awaitAllDelivered(api, List.of(id));
    }

    @Test
    void dishesBeingPreparedKeepTheirFinishTimeAcrossARestart() {
        var before = start();
        everyRecipeIs(api(before), Map.of("rice", 1), 1_200);
        var ids = placeOrders(api(before), 2);
        Awaitility.await().atMost(PATIENCE).until(() ->
                ids.stream().allMatch(id -> order(api(before), id).get("status").equals("PREPARING")));
        var asPrepared = ids.stream().map(id -> order(api(before), id)).toList();
        stop(before);

        var api = api(start());

        awaitAllDelivered(api, ids);
        for (var prepared : asPrepared) {
            var delivered = order(api, (String) prepared.get("id"));
            assertThat(delivered.get("preparingAt")).isEqualTo(prepared.get("preparingAt"));
            var due = Instant.parse((String) prepared.get("prepareUntil"));
            var deliveredAt = Instant.parse((String) delivered.get("deliveredAt"));
            assertThat(deliveredAt).isBetween(due, due.plusSeconds(10));
        }
    }

    @Test
    void twoInstancesRepairAStuckOrderOnceAndBuyNothingTwice(CapturedOutput output) {
        var first = api(start());
        var second = api(start());
        everyRecipeIs(first, Map.of("potato", 6), 100);
        marketSells(1);
        database().execute("""
                CREATE OR REPLACE FUNCTION fail_on_purpose() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'failing on purpose'; END $$ LANGUAGE plpgsql
                """);
        database().execute(
                "CREATE TRIGGER fail_on_purpose BEFORE INSERT ON purchase FOR EACH ROW EXECUTE FUNCTION fail_on_purpose()");

        var id = placeOrders(first, 1).getFirst();
        Awaitility.await().atMost(PATIENCE).until(() ->
                output.getOut().lines().anyMatch(line -> line.contains("gave up after 5 attempts")));
        database().execute("DROP TRIGGER fail_on_purpose ON purchase");

        awaitAllDelivered(second, List.of(id));
        assertThat(output.getOut().lines().filter(line -> line.contains("Repairing Order " + id)).count())
                .isEqualTo(1);
        assertThat(purchases(first)).hasSize(1);
        assertThat(new HashSet<>(purchases(second).stream().map(purchase -> purchase.get("id")).toList())).hasSize(1);
    }
}
