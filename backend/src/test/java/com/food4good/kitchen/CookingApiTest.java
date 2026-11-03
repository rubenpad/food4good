package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Two Cooks, with preparation times scaled down so 100 s of cooking takes 1 s. */
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=2",
    "food4good.kitchen.preparation-time-scale=0.01",
    "spring.flyway.placeholders.initial_stock=1000",
})
class CookingApiTest extends ApiTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    private void awaitDelivered(List<String> orders) {
        Awaitility.await().atMost(PATIENCE).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> orders.forEach(
                id -> assertThat(statusOf(id)).isEqualTo("DELIVERED")));
    }

    private static Instant at(Map<String, Object> order, String field) {
        return Instant.parse((String) order.get(field));
    }

    @Test
    void anOrderMovesThroughEveryStatusUntilDelivered() {
        givenEveryRecipeIs(Map.of("rice", 1), 100);

        var id = placeOrders(1).getFirst();

        var seen = new ArrayList<String>();
        Awaitility.await().atMost(PATIENCE).pollInterval(Duration.ofMillis(20)).until(() -> {
            var status = statusOf(id);
            if (seen.isEmpty() || !seen.getLast().equals(status)) {
                seen.add(status);
            }
            return status.equals("DELIVERED");
        });
        assertThat(seen).contains("PREPARING", "DELIVERED");
        assertThat(seen).isSubsetOf("WAITING_FOR_INGREDIENTS", "READY", "PREPARING", "DELIVERED");
        var order = order(id);
        assertThat(at(order, "acceptedAt")).isBeforeOrEqualTo(at(order, "readyAt"));
        assertThat(at(order, "readyAt")).isBeforeOrEqualTo(at(order, "preparingAt"));
        assertThat(at(order, "preparingAt")).isBefore(at(order, "deliveredAt"));
    }

    @Test
    void aDishTakesItsPreparationTimeTimesTheScale() {
        givenEveryRecipeIs(Map.of("rice", 1), 100);

        var id = placeOrders(1).getFirst();
        awaitDelivered(List.of(id));

        var order = order(id);
        var cooked = Duration.between(at(order, "preparingAt"), at(order, "deliveredAt"));
        assertThat(cooked).isBetween(Duration.ofMillis(1_000), Duration.ofMillis(2_500));
    }

    @Test
    void neverMoreOrdersPreparingThanCooks() {
        givenEveryRecipeIs(Map.of("rice", 1), 30);

        var ids = placeOrders(8);

        var busy = new ArrayList<Integer>();
        Awaitility.await().atMost(PATIENCE).pollInterval(Duration.ofMillis(50)).until(() -> {
            int busyCooks = getJson("/kitchen").read("$.busyCooks");
            busy.add(busyCooks);
            return ids.stream().allMatch(id -> statusOf(id).equals("DELIVERED"));
        });
        assertThat(busy).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(2)).contains(2);

        // The timestamps tell the whole story, including between the samples above.
        var changes = new TreeMap<Instant, Integer>();
        ids.stream().map(this::order).forEach(order -> {
            changes.merge(at(order, "preparingAt"), 1, Integer::sum);
            changes.merge(at(order, "deliveredAt"), -1, Integer::sum);
        });
        var cooking = 0;
        for (var change : changes.values()) {
            cooking += change;
            assertThat(cooking).isLessThanOrEqualTo(2);
        }
    }

    @Test
    void aFreeCookTakesTheOrderThatBecameReadyEarliest() {
        givenEveryRecipeIs(Map.of("rice", 1), 20);

        var ids = placeOrders(8);
        awaitDelivered(ids);

        var byReadyTime = ids.stream()
                .map(this::order)
                .sorted(Comparator.comparing(order -> at(order, "readyAt")))
                .map(order -> at(order, "preparingAt"))
                .toList();
        assertThat(byReadyTime).isSorted();
    }

    @Test
    void cooksDoNotWaitForAnOlderOrderThatIsStillShortOfIngredients() {
        givenEveryRecipeIs(Map.of("meat", 2_000), 100);
        var shortOfMeat = placeOrders(1).getFirst();
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(stockOf("meat").get(2)).isZero());

        givenEveryRecipeIs(Map.of("rice", 1), 10);
        var later = placeOrders(1).getFirst();

        awaitDelivered(List.of(later));
        assertThat(statusOf(shortOfMeat)).isEqualTo("WAITING_FOR_INGREDIENTS");
    }

    @Test
    void preparingConsumesTheReservationFromStock() {
        givenEveryRecipeIs(Map.of("tomato", 3, "onion", 2), 10);

        awaitDelivered(placeOrders(1));

        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
            assertThat(stockOf("tomato")).containsExactly(997, 0, 997);
            assertThat(stockOf("onion")).containsExactly(998, 0, 998);
        });
        assertStockRulesHold();
    }

    @Test
    void manyOrdersAllGetDeliveredAndUseExactlyTheirIngredients() {
        givenEveryRecipeIs(Map.of("cheese", 2), 5);

        awaitDelivered(placeOrders(20));

        Awaitility.await().atMost(PATIENCE).untilAsserted(
                () -> assertThat(stockOf("cheese")).containsExactly(960, 0, 960));
        List<?> preparing = getJson("/orders?status=PREPARING").read("$.items");
        assertThat(preparing).isEmpty();
    }

    @Test
    void kitchenReportsCooksAndOrdersByStatus() {
        givenEveryRecipeIs(Map.of("rice", 1), 500);

        var ids = placeOrders(5);

        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
            var kitchen = getJson("/kitchen");
            assertThat((Integer) kitchen.read("$.cooks")).isEqualTo(2);
            assertThat((Integer) kitchen.read("$.busyCooks")).isEqualTo(2);
            assertThat((Integer) kitchen.read("$.ordersWaitingForIngredients")).isZero();
            assertThat((Integer) kitchen.read("$.ordersReady")).isEqualTo(3);
            assertThat((Integer) kitchen.read("$.ordersPreparing")).isEqualTo(2);
        });
        assertThat(order(ids.getFirst()).keySet()).contains("prepareUntil");
    }
}
