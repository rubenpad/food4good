package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

/** Real Cooks, at real speed: the Backlog falls while they work. */
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=2",
    "food4good.kitchen.max-wait=10m",
    "spring.flyway.placeholders.initial_stock=1000",
})
class KitchenCapacityWithCooksApiTest extends ApiTest {

    private int kitchen(String field) {
        return getJson("/kitchen").read("$." + field);
    }

    @Test
    void capacityAndTheEstimatedWaitAllowForEveryCook() {
        givenEveryRecipeIs(Map.of("rice", 1), 60);

        var response = send(HttpMethod.POST, "/orders", Map.of("quantity", 25));

        assertThat((Integer) JsonPath.read(response.getBody(), "$.accepted")).isEqualTo(20);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.estimatedWaitSeconds")).isEqualTo(600);
    }

    @Test
    void anOrderBeingPreparedCountsOnlyItsRemainingTime() {
        givenEveryRecipeIs(Map.of("rice", 1), 30);

        placeOrders(2);

        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> kitchen("busyCooks") == 2);
        var early = kitchen("backlogSeconds");
        assertThat(early).isBetween(25, 30);
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> kitchen("backlogSeconds") <= early - 3);
        assertThat(kitchen("busyCooks")).isEqualTo(2);
    }
}
