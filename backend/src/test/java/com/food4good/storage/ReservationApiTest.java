package com.food4good.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ReservationApiTest extends ApiTest {

    @Test
    void foodStorageStartsWithFiveUnitsOfEveryIngredient() {
        List<Map<String, Object>> stock = getJson("/storage").read("$");

        assertThat(stock).extracting(row -> row.get("ingredient")).containsExactlyInAnyOrder(
                "tomato", "lemon", "potato", "rice", "ketchup", "lettuce", "onion", "cheese", "meat", "chicken");
        assertThat(stock).allSatisfy(row -> {
            assertThat(row).containsEntry("onHand", 5);
            assertThat(row).containsEntry("reserved", 0);
            assertThat(row).containsEntry("available", 5);
        });
    }

    @Test
    void anOrderWithEnoughStockReservesItAndBecomesReady() {
        givenEveryRecipeIs(Map.of("tomato", 2, "rice", 1), 60);

        var order = placeOrders(1).getFirst();

        Awaitility.await().untilAsserted(() -> assertThat(statusOf(order)).isEqualTo("READY"));
        assertThat(order(order).get("readyAt")).isNotNull();
        assertThat(stockOf("tomato")).containsExactly(5, 2, 3);
        assertThat(stockOf("rice")).containsExactly(5, 1, 4);
        assertThat(stockOf("onion")).containsExactly(5, 0, 5);
    }

    @Test
    void twoOrdersCompetingForFiveTomatoesNeverShareAUnit() {
        givenEveryRecipeIs(Map.of("tomato", 3), 60);

        var orders = placeOrders(2);

        Awaitility.await().untilAsserted(() -> {
            assertThat(stockOf("tomato")).containsExactly(5, 5, 0);
            assertThat(orders.stream().map(this::statusOf))
                    .containsExactlyInAnyOrder("READY", "WAITING_FOR_INGREDIENTS");
        });
    }

    @Test
    void anOrderShortOfOneIngredientStillReservesTheRest() {
        givenEveryRecipeIs(Map.of("meat", 6, "onion", 2), 60);

        var order = placeOrders(1).getFirst();

        Awaitility.await().untilAsserted(() -> {
            assertThat(stockOf("meat")).containsExactly(5, 5, 0);
            assertThat(stockOf("onion")).containsExactly(5, 2, 3);
        });
        assertThat(statusOf(order)).isEqualTo("WAITING_FOR_INGREDIENTS");
    }

    @Test
    void editingARecipeChangesNeitherAnAcceptedOrderNorItsReservation() {
        givenEveryRecipeIs(Map.of("potato", 2), 60);
        var before = placeOrders(1).getFirst();
        Awaitility.await().untilAsserted(() -> assertThat(statusOf(before)).isEqualTo("READY"));
        var accepted = order(before);

        var edited = send(HttpMethod.PUT, "/recipes/" + accepted.get("recipeId"), Map.of(
                "name", "Renamed After Acceptance",
                "preparationTimeSeconds", 999,
                "ingredients", List.of(Map.of("ingredient", "potato", "quantity", 5))));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(order(before)).isEqualTo(accepted);
        assertThat(stockOf("potato")).containsExactly(5, 2, 3);
    }

    @Test
    void anOrderStillWaitingKeepsTheQuantitiesItWasAcceptedWith() {
        givenEveryRecipeIs(Map.of("lemon", 7), 60);
        var waiting = placeOrders(1).getFirst();
        Awaitility.await().untilAsserted(() -> assertThat(stockOf("lemon")).containsExactly(5, 5, 0));
        var accepted = order(waiting);

        givenEveryRecipeIs(Map.of("lemon", 1), 5);

        assertThat(order(waiting)).isEqualTo(accepted);
        assertThat(statusOf(waiting)).isEqualTo("WAITING_FOR_INGREDIENTS");
    }

    @Test
    void newOrdersUseTheEditedRecipe() {
        givenEveryRecipeIs(Map.of("chicken", 1), 60);

        givenEveryRecipeIs(Map.of("ketchup", 2), 30);
        var order = placeOrders(1).getFirst();

        Awaitility.await().untilAsserted(() -> assertThat(statusOf(order)).isEqualTo("READY"));
        assertThat(order(order)).containsEntry("preparationTimeSeconds", 30);
        assertThat(stockOf("ketchup")).containsExactly(5, 2, 3);
        assertThat(stockOf("chicken")).containsExactly(5, 0, 5);
    }
}
