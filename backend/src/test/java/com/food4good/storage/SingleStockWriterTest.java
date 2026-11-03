package com.food4good.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.Food4GoodApplication;
import com.food4good.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Two app instances on one database, driven through their APIs and observed through their logs. */
@ExtendWith(OutputCaptureExtension.class)
class SingleStockWriterTest {

    private static final String BECAME_WRITER = "This instance is now the stock writer";
    private static final int PLENTY = 1_000;

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgres();
    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();

    @BeforeEach
    void startDatabase() {
        postgres.start();
    }

    @AfterEach
    void stopEverything() {
        instances.forEach(ConfigurableApplicationContext::close);
        postgres.stop();
    }

    private ConfigurableApplicationContext startInstance() {
        var instance = new SpringApplicationBuilder(Food4GoodApplication.class).run(
                "--server.port=0",
                "--spring.flyway.placeholders.initial_stock=" + PLENTY,
                "--food4good.kitchen.cooks=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword());
        instances.add(instance);
        return instance;
    }

    private static RestClient api(ConfigurableApplicationContext instance) {
        return RestClient.create("http://localhost:" + instance.getEnvironment().getProperty("local.server.port") + "/api");
    }

    private static void everyRecipeNeedsOneCheese(RestClient api) {
        List<Map<String, Object>> recipes = JsonPath.read(api.get().uri("/recipes").retrieve().body(String.class), "$");
        recipes.forEach(recipe -> api.put().uri("/recipes/{id}", recipe.get("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", recipe.get("name"),
                        "preparationTimeSeconds", 60,
                        "ingredients", List.of(Map.of("ingredient", "cheese", "quantity", 1))))
                .retrieve().toBodilessEntity());
    }

    private static List<String> placeOrders(RestClient api, int quantity) {
        var body = api.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity)).retrieve().body(String.class);
        return JsonPath.read(body, "$.orderIds");
    }

    private static List<Integer> cheese(RestClient api) {
        var body = api.get().uri("/storage").retrieve().body(String.class);
        Map<String, Integer> row = JsonPath.<List<Map<String, Integer>>>read(body, "$[?(@.ingredient == 'cheese')]").getFirst();
        return List.of(row.get("onHand"), row.get("reserved"), row.get("available"));
    }

    private static int ready(RestClient api) {
        List<?> orders = JsonPath.read(api.get().uri("/orders?status=READY&limit=200").retrieve().body(String.class), "$.items");
        return orders.size();
    }

    private static long timesBecameWriter(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains(BECAME_WRITER)).count();
    }

    @Test
    void onlyOneInstanceIsTheWriter(CapturedOutput output) throws Exception {
        startInstance();
        startInstance();

        Awaitility.await().untilAsserted(() -> assertThat(timesBecameWriter(output)).isEqualTo(1));
        // Longer than the standby's retry interval, so a second writer would have shown up.
        Thread.sleep(3_000);
        assertThat(timesBecameWriter(output)).isEqualTo(1);
    }

    @Test
    void ordersPlacedOnTwoInstancesAtOnceAreEachReservedExactlyOnce() throws Exception {
        var first = api(startInstance());
        var second = api(startInstance());
        everyRecipeNeedsOneCheese(first);

        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Callable<List<String>>> requests = IntStream.range(0, 20)
                    .<Callable<List<String>>>mapToObj(i -> () -> placeOrders(i % 2 == 0 ? first : second, 5))
                    .toList();
            for (var future : executor.invokeAll(requests)) {
                assertThat(future.get()).hasSize(5);
            }
        }

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(ready(first)).isEqualTo(100);
            assertThat(cheese(first)).containsExactly(PLENTY, 100, PLENTY - 100);
        });
        assertThat(cheese(second)).containsExactly(PLENTY, 100, PLENTY - 100);
    }

    @Test
    void anotherInstanceTakesOverWhenTheWriterStops(CapturedOutput output) {
        var writer = startInstance();
        Awaitility.await().untilAsserted(() -> assertThat(timesBecameWriter(output)).isEqualTo(1));
        var standby = api(startInstance());
        everyRecipeNeedsOneCheese(standby);

        writer.close();
        instances.remove(writer);

        Awaitility.await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(timesBecameWriter(output)).isEqualTo(2));
        placeOrders(standby, 1);
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(cheese(standby)).containsExactly(PLENTY, 1, PLENTY - 1);
            assertThat(ready(standby)).isEqualTo(1);
        });
    }
}
