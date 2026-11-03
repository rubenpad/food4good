package com.food4good.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.food4good.LiveEvents;
import com.food4good.kitchen.domain.ports.out.OrderChangesPort;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Two Cooks, and cooking fast enough to watch Orders go all the way through. */
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=2",
    "food4good.kitchen.preparation-time-scale=0.01",
    "food4good.kitchen.max-wait=1000h",
    "spring.flyway.placeholders.initial_stock=100000",
})
class LiveUpdatesApiTest extends ApiTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    @Autowired
    private OrderChangesPort orderChanges;

    @Autowired
    private TransactionPort transactions;

    @Test
    void aClientIsToldEveryStatusAnOrderReaches() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 2), 100);

        try (var events = subscribeToEvents()) {
            var id = placeOrders(1).getFirst();

            Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(statusesOf(events, id)).contains("DELIVERED"));
            assertThat(statusesOf(events, id)).contains("PREPARING");
            var delivered = events.orders().stream().filter(order -> order.get("id").equals(id)).toList().getLast();
            assertThat(delivered).isEqualTo(order(id));
        }
    }

    private static List<String> statusesOf(LiveEvents events, String id) {
        return events.orders().stream()
                .filter(update -> update.get("id").equals(id))
                .map(update -> (String) update.get("status"))
                .toList();
    }

    @Test
    void aClientIsToldAboutAnOrderItAcceptedEvenIfNothingElseHappensToIt() throws Exception {
        givenEveryRecipeIs(Map.of("meat", 200_000), 100);

        try (var events = subscribeToEvents()) {
            var id = placeOrders(1).getFirst();

            Awaitility.await().atMost(PATIENCE).untilAsserted(
                    () -> assertThat(statusesOf(events, id)).contains("WAITING_FOR_INGREDIENTS"));
        }
    }

    @Test
    void aClientIsToldWhenAnOrderBecomesReady() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 1), 10_000);

        try (var events = subscribeToEvents()) {
            var ids = placeOrders(4);

            // Two Cooks and long Dishes, so two Orders stay ready.
            Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
                var ready = ids.stream().filter(id -> statusesOf(events, id).contains("READY")).count();
                assertThat(ready).isGreaterThanOrEqualTo(2);
            });
        }
    }

    @Test
    void aClientIsToldWhenAnOrderThatWaitedForACookStartsPreparing() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 1), 300);

        try (var events = subscribeToEvents()) {
            var ids = placeOrders(4);

            // Two Cooks and 3 s Dishes: two Orders sit ready well past the next batch, then start.
            Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(ids.stream()
                            .filter(id -> {
                                var statuses = statusesOf(events, id);
                                return statuses.contains("READY") && statuses.contains("PREPARING");
                            })
                            .count())
                    .isGreaterThanOrEqualTo(2));
        }
    }

    @Test
    void aClientIsToldWhatFoodStorageHolds() throws Exception {
        givenEveryRecipeIs(Map.of("cheese", 3), 10);

        try (var events = subscribeToEvents()) {
            placeOrders(2);

            Awaitility.await().atMost(PATIENCE).untilAsserted(
                    () -> assertThat(events.latestStockOf("cheese")).containsExactly(99_994, 0, 99_994));
            List<Map<String, Object>> rows = JsonPath.read(events.batches("storage").getLast().data(), "$");
            assertThat(rows).hasSize(10);
            assertThat(rows).isEqualTo(getJson("/storage").read("$"));
        }
    }

    @Test
    void aBurstArrivesInBatchesAtMostOnePerTypeEveryQuarterSecond() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 1), 100);

        try (var events = subscribeToEvents()) {
            var ids = new ArrayList<String>();
            for (var i = 0; i < 20; i++) {
                ids.addAll(placeOrders(10));
            }

            Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(
                            events.orders().stream().map(order -> order.get("id")).toList())
                    .containsAll(ids));
            for (var type : List.of("orders", "storage")) {
                var batches = events.batches(type);
                assertThat(batches).hasSizeGreaterThan(1);
                for (var i = 1; i < batches.size(); i++) {
                    var gap = batches.get(i).receivedAtMillis() - batches.get(i - 1).receivedAtMillis();
                    // Delivery can bunch up slightly; the batches themselves are 250 ms apart.
                    assertThat(gap).isGreaterThanOrEqualTo(150);
                }
            }
            assertThat(events.batches("orders").size()).isLessThan(ids.size());
        }
    }

    @Test
    void batchIdsAlwaysIncrease() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 1), 10);

        try (var events = subscribeToEvents()) {
            var id = placeOrders(1).getFirst();

            Awaitility.await().atMost(PATIENCE).until(() -> statusesOf(events, id).contains("DELIVERED"));
            var batchIds = events.allBatches().stream().map(LiveEvents.Batch::id).toList();
            assertThat(batchIds).isSorted().doesNotHaveDuplicates();
        }
    }

    @Test
    void everyClientGetsTheUpdatesAndALeavingClientAffectsNobody() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 1), 10);

        try (var staying = subscribeToEvents()) {
            var leaving = subscribeToEvents();
            var first = placeOrders(1).getFirst();
            Awaitility.await().atMost(PATIENCE).until(
                    () -> statusesOf(staying, first).contains("DELIVERED") && statusesOf(leaving, first).contains("DELIVERED"));

            leaving.close();
            var second = placeOrders(1).getFirst();

            Awaitility.await().atMost(PATIENCE).until(() -> statusesOf(staying, second).contains("DELIVERED"));
        }
    }

    /**
     * The one test here that reaches past the API: a rollback can't be provoked from outside. It
     * checks the promise the services rely on, using the port they call.
     */
    @Test
    void aChangeInATransactionThatRollsBackIsNeverSent() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 1), 10);
        var id = placeOrders(1).getFirst();
        Awaitility.await().atMost(PATIENCE).until(() -> statusOf(id).equals("DELIVERED"));
        Thread.sleep(600);

        try (var events = subscribeToEvents()) {
            try {
                transactions.inTransaction(() -> {
                    orderChanges.changed(List.of(UUID.fromString(id)));
                    throw new IllegalStateException("roll back");
                });
            } catch (IllegalStateException expected) {
                // The rollback is the point.
            }
            Thread.sleep(800);
            assertThat(events.batches("orders")).isEmpty();

            transactions.inTransaction(() -> orderChanges.changed(List.of(UUID.fromString(id))));
            Awaitility.await().atMost(PATIENCE).until(() -> !events.batches("orders").isEmpty());
        }
    }

    @Test
    void aFreshClientReceivesNothingUntilSomethingChanges() throws Exception {
        Thread.sleep(500);
        try (var events = subscribeToEvents()) {
            Thread.sleep(800);
            assertThat(events.batches("orders")).isEmpty();
            assertThat(events.batches("storage")).isEmpty();
        }
    }
}
