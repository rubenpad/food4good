package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** No Cooks, so nothing cooks and the Backlog holds still. Zero Cooks count as one for capacity. */
@TestPropertySource(properties = {"food4good.kitchen.cooks=0", "food4good.kitchen.max-wait=10m"})
class KitchenCapacityApiTest extends ApiTest {

    private ResponseEntity<String> place(int quantity) {
        var response = send(HttpMethod.POST, "/orders", Map.of("quantity", quantity));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private static int number(ResponseEntity<String> response, String path) {
        return JsonPath.read(response.getBody(), path);
    }

    private int kitchen(String field) {
        return getJson("/kitchen").read("$." + field);
    }

    private int ordersInHistory() {
        List<?> orders = getJson("/orders?limit=200").read("$.items");
        return orders.size();
    }

    @Test
    void acceptsOnlyWhatFitsWithinTheMaximumWait() {
        givenEveryRecipeIs(Map.of("rice", 1), 60);

        var response = place(15);

        assertThat(number(response, "$.requested")).isEqualTo(15);
        assertThat(number(response, "$.accepted")).isEqualTo(10);
        assertThat(number(response, "$.estimatedWaitSeconds")).isEqualTo(600);
        List<String> ids = JsonPath.read(response.getBody(), "$.orderIds");
        assertThat(ids).hasSize(10);
        assertThat(ordersInHistory()).isEqualTo(10);
    }

    @Test
    void aFullKitchenAcceptsNothingAndCreatesNothing() {
        givenEveryRecipeIs(Map.of("rice", 1), 60);
        place(10);

        var response = place(3);

        assertThat(number(response, "$.requested")).isEqualTo(3);
        assertThat(number(response, "$.accepted")).isZero();
        assertThat(number(response, "$.estimatedWaitSeconds")).isEqualTo(600);
        List<String> ids = JsonPath.read(response.getBody(), "$.orderIds");
        assertThat(ids).isEmpty();
        assertThat(ordersInHistory()).isEqualTo(10);
    }

    @Test
    void anOrderThatExactlyReachesTheMaximumWaitFits() {
        givenEveryRecipeIs(Map.of("rice", 1), 600);

        assertThat(number(place(1), "$.accepted")).isEqualTo(1);
        assertThat(number(place(1), "$.accepted")).isZero();
    }

    @Test
    void anOrderLongerThanTheRemainingRoomIsNotAccepted() {
        givenEveryRecipeIs(Map.of("rice", 1), 60);
        place(9);

        givenEveryRecipeIs(Map.of("rice", 1), 120);

        assertThat(number(place(5), "$.accepted")).isZero();
    }

    @Test
    void aRecipeLongerThanTheMaximumWaitIsNeverAccepted() {
        givenEveryRecipeIs(Map.of("rice", 1), 601);

        assertThat(number(place(1), "$.accepted")).isZero();
    }

    @Test
    void concurrentRequestsNeverOverbookTheKitchen() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 1), 60);

        var accepted = new ArrayList<Integer>();
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Callable<Integer>> requests = IntStream.range(0, 20)
                    .<Callable<Integer>>mapToObj(i -> () -> number(place(5), "$.accepted"))
                    .toList();
            for (var future : executor.invokeAll(requests)) {
                accepted.add(future.get());
            }
        }

        assertThat(accepted.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
        assertThat(ordersInHistory()).isEqualTo(10);
        assertThat(kitchen("backlogSeconds")).isEqualTo(600);
    }

    @Test
    void theBacklogCountsOrdersWaitingForIngredientsAtTheirPreparationTimeOnly() {
        givenEveryRecipeIs(Map.of("rice", 100), 60);

        place(3);

        assertThat(kitchen("backlogSeconds")).isEqualTo(180);
    }

    @Test
    void kitchenReportsTheMaximumWaitBacklogAndEstimatedCapacity() {
        givenEveryRecipeIs(Map.of("rice", 1), 60);
        assertThat(kitchen("maxWaitSeconds")).isEqualTo(600);
        assertThat(kitchen("backlogSeconds")).isZero();
        assertThat(kitchen("estimatedCapacity")).isEqualTo(10);

        place(4);

        assertThat(kitchen("backlogSeconds")).isEqualTo(240);
        assertThat(kitchen("estimatedCapacity")).isEqualTo(6);
    }

    @Test
    void theEstimateUsesTheAveragePreparationTimeAndNeverGoesNegative() {
        List<Map<String, Object>> recipes = getJson("/recipes").read("$");
        for (var i = 0; i < recipes.size(); i++) {
            send(HttpMethod.PUT, "/recipes/" + recipes.get(i).get("id"), Map.of(
                    "name", recipes.get(i).get("name"),
                    "preparationTimeSeconds", i % 2 == 0 ? 60 : 120,
                    "ingredients", List.of(Map.of("ingredient", "rice", "quantity", 1))));
        }
        assertThat(kitchen("estimatedCapacity")).isEqualTo(6);

        givenEveryRecipeIs(Map.of("rice", 1), 601);
        assertThat(kitchen("estimatedCapacity")).isZero();
    }
}
