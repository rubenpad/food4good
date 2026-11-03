package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

// No Cooks, so Orders stop at ready and stock stays reserved. Capacity isn't what these tests are about.
@TestPropertySource(properties = {"food4good.kitchen.cooks=0", "food4good.kitchen.max-wait=1000h"})
class PlacingOrdersApiTest extends ApiTest {

    @Test
    void placesOneOrderForEachUnitOfQuantity() {
        var response = send(HttpMethod.POST, "/orders", Map.of("quantity", 3));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.requested")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(response.getBody(), "$.accepted")).isEqualTo(3);
        List<String> ids = JsonPath.read(response.getBody(), "$.orderIds");
        assertThat(ids).hasSize(3).doesNotHaveDuplicates();
        ids.forEach(id -> {
            var order = order(id);
            assertThat(order.get("recipe")).isNotNull();
            assertThat(order.get("recipeId")).isNotNull();
            assertThat(order.get("acceptedAt")).isNotNull();
            assertThat((Integer) order.get("preparationTimeSeconds")).isPositive();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"quantity\":0}",
            "{\"quantity\":-1}",
            "{\"quantity\":10001}",
            "{\"quantity\":1.5}",
            "{\"quantity\":\"lots\"}",
            "{\"quantity\":null}",
            "{}",
            "not json",
    })
    void refusesInvalidQuantitiesAndCreatesNothing(String body) {
        var response = send(HttpMethod.POST, "/orders", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isNotBlank();
        List<?> orders = getJson("/orders").read("$.items");
        assertThat(orders).isEmpty();
    }

    @Test
    void drawsRecipesAtRandomFromTheCurrentRecipes() {
        var recipeIds = placeOrders(200).stream().map(id -> order(id).get("recipe")).distinct().toList();
        List<String> names = getJson("/recipes").read("$[*].name");

        // Missing any one of six in 200 fair draws is about a 1 in 10^15 chance.
        assertThat(recipeIds).containsExactlyInAnyOrderElementsOf(names);
    }

    @Test
    void aNewRecipeJoinsTheDraw() {
        send(HttpMethod.POST, "/recipes", Map.of(
                "name", "Brand New",
                "preparationTimeSeconds", 10,
                "ingredients", List.of(Map.of("ingredient", "rice", "quantity", 1))));

        var names = placeOrders(200).stream().map(id -> order(id).get("recipe")).distinct().toList();

        assertThat(names).contains("Brand New");
    }

    @Test
    void historyIsNewestFirstAndPagesWithoutGapsOrRepeats() {
        var first = placeOrders(3);
        var second = placeOrders(3);

        var seen = new ArrayList<String>();
        String cursor = null;
        do {
            var page = getJson("/orders?limit=2" + (cursor == null ? "" : "&cursor=" + cursor));
            List<String> ids = page.read("$.items[*].id");
            assertThat(ids).hasSizeLessThanOrEqualTo(2);
            seen.addAll(ids);
            cursor = page.read("$.nextCursor");
        } while (cursor != null);

        assertThat(seen).hasSize(6).doesNotHaveDuplicates();
        assertThat(seen.subList(0, 3)).containsExactlyInAnyOrderElementsOf(second);
        assertThat(seen.subList(3, 6)).containsExactlyInAnyOrderElementsOf(first);
    }

    @Test
    void historyFiltersByStatus() {
        givenEveryRecipeIs(Map.of("cheese", 3), 60);
        var ready = placeOrders(1).getFirst();
        Awaitility.await().untilAsserted(() -> assertThat(statusOf(ready)).isEqualTo("READY"));
        var waiting = placeOrders(1).getFirst();
        Awaitility.await().untilAsserted(() -> assertThat(stockOf("cheese").get(2)).isZero());

        List<String> readyIds = getJson("/orders?status=READY").read("$.items[*].id");
        List<String> waitingIds = getJson("/orders?status=WAITING_FOR_INGREDIENTS").read("$.items[*].id");
        List<String> deliveredIds = getJson("/orders?status=DELIVERED").read("$.items[*].id");

        assertThat(readyIds).containsExactly(ready);
        assertThat(waitingIds).containsExactly(waiting);
        assertThat(deliveredIds).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"status=COOKED", "limit=0", "limit=201", "limit=lots", "cursor=not-a-cursor"})
    void refusesInvalidHistoryQueries(String query) {
        var response = api().get().uri("/orders?" + query).retrieve()
                .onStatus(status -> true, (request, reply) -> {
                })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isNotBlank();
    }

    @Test
    void concurrentRequestsLoseNothingAndNeverBreakTheStockRules() throws Exception {
        givenEveryRecipeIs(Map.of("cheese", 1), 60);

        var ids = new ArrayList<String>();
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Callable<List<String>>> requests = IntStream.range(0, 20)
                    .<Callable<List<String>>>mapToObj(i -> () -> placeOrders(5))
                    .toList();
            for (var future : executor.invokeAll(requests)) {
                ids.addAll(future.get());
            }
        }

        assertThat(ids).hasSize(100).doesNotHaveDuplicates();
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(stockOf("cheese")).containsExactly(5, 5, 0);
            List<?> ready = getJson("/orders?status=READY&limit=200").read("$.items");
            assertThat(ready).hasSize(5);
        });
        List<?> waiting = getJson("/orders?status=WAITING_FOR_INGREDIENTS&limit=200").read("$.items");
        assertThat(waiting).hasSize(95);
        assertStockRulesHold();
    }
}
