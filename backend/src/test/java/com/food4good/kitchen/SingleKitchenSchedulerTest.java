package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.Food4GoodApplication;
import com.food4good.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
class SingleKitchenSchedulerTest {

    private static final String BECAME_SCHEDULER = "This instance is now the kitchen scheduler";

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
                "--spring.flyway.placeholders.initial_stock=1000",
                "--food4good.kitchen.cooks=2",
                "--food4good.kitchen.preparation-time-scale=0.01",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword());
        instances.add(instance);
        return instance;
    }

    private static RestClient api(ConfigurableApplicationContext instance) {
        return RestClient.create("http://localhost:" + instance.getEnvironment().getProperty("local.server.port") + "/api");
    }

    private static long timesBecameScheduler(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains(BECAME_SCHEDULER)).count();
    }

    private static void everyRecipeTakes(RestClient api, int seconds) {
        List<Map<String, Object>> recipes = JsonPath.read(api.get().uri("/recipes").retrieve().body(String.class), "$");
        recipes.forEach(recipe -> api.put().uri("/recipes/{id}", recipe.get("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", recipe.get("name"),
                        "preparationTimeSeconds", seconds,
                        "ingredients", List.of(Map.of("ingredient", "rice", "quantity", 1))))
                .retrieve().toBodilessEntity());
    }

    private static void placeOrders(RestClient api, int quantity) {
        api.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity)).retrieve().toBodilessEntity();
    }

    private static int count(RestClient api, String status) {
        List<?> orders = JsonPath.read(
                api.get().uri("/orders?status=" + status + "&limit=200").retrieve().body(String.class), "$.items");
        return orders.size();
    }

    @Test
    void onlyOneInstanceIsTheScheduler(CapturedOutput output) throws Exception {
        startInstance();
        startInstance();

        Awaitility.await().untilAsserted(() -> assertThat(timesBecameScheduler(output)).isEqualTo(1));
        // Longer than the standby's retry interval, so a second scheduler would have shown up.
        Thread.sleep(3_000);
        assertThat(timesBecameScheduler(output)).isEqualTo(1);
    }

    @Test
    void twoInstancesTogetherNeverExceedTheCooks() {
        var first = api(startInstance());
        var second = api(startInstance());
        everyRecipeTakes(first, 30);

        placeOrders(first, 5);
        placeOrders(second, 5);

        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(30)).until(() -> {
            assertThat(count(first, "PREPARING")).isLessThanOrEqualTo(2);
            return count(second, "DELIVERED") == 10;
        });
    }

    @Test
    void anotherInstanceTakesOverAndFinishesWhatWasCooking(CapturedOutput output) {
        var scheduler = startInstance();
        Awaitility.await().untilAsserted(() -> assertThat(timesBecameScheduler(output)).isEqualTo(1));
        var standby = api(startInstance());
        everyRecipeTakes(standby, 200);
        placeOrders(standby, 4);
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> count(standby, "PREPARING") == 2);

        scheduler.close();
        instances.remove(scheduler);

        Awaitility.await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(timesBecameScheduler(output)).isEqualTo(2));
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> count(standby, "DELIVERED") == 4);
    }
}
