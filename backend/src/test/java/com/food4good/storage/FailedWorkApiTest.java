package com.food4good.storage;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Defects are simulated with database triggers that make one kind of write fail. Food Storage
 * starts with 5 of each Ingredient, and the reconciler checks every half second.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=2",
    "food4good.kitchen.preparation-time-scale=0.01",
    "food4good.storage.reconcile-interval=500ms",
})
class FailedWorkApiTest extends ApiTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);
    private static final String BUY = "/api/farmers-market/buy";

    @RegisterExtension
    static final WireMockExtension market = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void marketUrl(DynamicPropertyRegistry registry) {
        registry.add("food4good.market.url", market::baseUrl);
    }

    private void marketSells(int quantity) {
        market.stubFor(get(urlPathEqualTo(BUY)).willReturn(okJson("{\"quantitySold\": " + quantity + "}")));
    }

    private int marketCalls() {
        return market.findAll(getRequestedFor(urlPathEqualTo(BUY))).size();
    }

    private static long count(CapturedOutput output, String text) {
        return output.getOut().lines().filter(line -> line.contains(text)).count();
    }

    private void awaitDelivered(String order) {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(statusOf(order)).isEqualTo("DELIVERED"));
    }

    private int purchased(String ingredient) {
        List<Map<String, Object>> purchases = getJson("/purchases?limit=200").read("$.items");
        return purchases.stream()
                .filter(purchase -> purchase.get("ingredient").equals(ingredient))
                .mapToInt(purchase -> (Integer) purchase.get("quantity"))
                .sum();
    }

    @Test
    void aReservationThatKeepsFailingStopsAfterFiveAttemptsAndIsRepairedOnceFixed(CapturedOutput output) {
        givenEveryRecipeIs(Map.of("rice", 2), 10);
        failWrites("reservation_line", "UPDATE", "true");

        var order = placeOrders(1).getFirst();

        Awaitility.await().atMost(PATIENCE).until(() -> count(output, "gave up after 5 attempts") >= 1);
        assertThat(statusOf(order)).isEqualTo("WAITING_FOR_INGREDIENTS");

        stopFailing("reservation_line");
        awaitDelivered(order);
        assertThat(count(output, "Repairing Order " + order)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ordersWhoseIngredientRequestFailedAreRepairedFromThatRequest(CapturedOutput output) {
        givenEveryRecipeIs(Map.of("onion", 2, "tomato", 1), 10);
        marketSells(5);
        failWrites("reservation_line", "INSERT", "true");

        var orders = placeOrders(3);

        Awaitility.await().atMost(PATIENCE).until(() -> count(output, "gave up after 5 attempts") >= 1);
        orders.forEach(order -> assertThat(statusOf(order)).isEqualTo("WAITING_FOR_INGREDIENTS"));

        stopFailing("reservation_line");
        orders.forEach(this::awaitDelivered);
        // 3 Orders × 2 onions from the starting 5: the shortfall was bought, and nothing is left reserved.
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(stockOf("onion").get(1)).isZero());
    }

    @Test
    void aPurchaseThatFailedToReachStockIsBoughtAgain(CapturedOutput output) {
        givenEveryRecipeIs(Map.of("meat", 6), 10);
        marketSells(1);
        failWrites("purchase", "INSERT", "true");

        var order = placeOrders(1).getFirst();

        Awaitility.await().atMost(PATIENCE).until(() -> count(output, "gave up after 5 attempts") >= 1);
        assertThat(statusOf(order)).isEqualTo("WAITING_FOR_INGREDIENTS");
        var callsBeforeTheFix = marketCalls();

        stopFailing("purchase");
        awaitDelivered(order);
        assertThat(marketCalls()).isGreaterThan(callsBeforeTheFix);
        // The first sale never reached stock: the accepted cost of a lost sale.
        assertThat(purchased("meat")).isEqualTo(1);
    }

    @Test
    void aMarketJobThatKeepsFailingStopsAfterFiveErrorsAndIsRepairedOnceFixed(CapturedOutput output) {
        givenEveryRecipeIs(Map.of("cheese", 6), 10);
        marketSells(1);
        failWrites("market_job", "UPDATE", "NEW.remaining <> OLD.remaining");

        var order = placeOrders(1).getFirst();

        Awaitility.await().atMost(PATIENCE).until(() -> count(output, "Market job") >= 5);
        assertThat(statusOf(order)).isEqualTo("WAITING_FOR_INGREDIENTS");

        stopFailing("market_job");
        awaitDelivered(order);
        assertThat(count(output, "Repairing Order " + order)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void zeroSalesNeverCountAsFailuresAndAreLeftAlone(CapturedOutput output) throws Exception {
        givenEveryRecipeIs(Map.of("lemon", 6), 10);
        marketSells(0);

        var order = placeOrders(1).getFirst();

        // Several reconciler rounds, and more than 5 zero sales.
        Awaitility.await().atMost(PATIENCE).until(() -> marketCalls() >= 7);
        Thread.sleep(1_500);
        assertThat(count(output, "Repairing Order " + order)).isZero();
        assertThat(count(output, "Market job")).isZero();

        marketSells(1);
        awaitDelivered(order);
        assertThat(count(output, "Repairing Order " + order)).isZero();
    }

    @Test
    void repairsAreLimitedSoARepeatingDefectCantKeepBuying(CapturedOutput output) throws Exception {
        givenEveryRecipeIs(Map.of("chicken", 6), 10);
        marketSells(1);
        failWrites("purchase", "INSERT", "true");

        var order = placeOrders(1).getFirst();

        Awaitility.await().atMost(Duration.ofSeconds(60)).until(() -> count(output, "Giving up on Order " + order) == 1);
        assertThat(count(output, "Repairing Order " + order)).isEqualTo(3);
        var calls = marketCalls();
        Thread.sleep(2_000);
        assertThat(marketCalls()).isEqualTo(calls);
        assertThat(statusOf(order)).isEqualTo("WAITING_FOR_INGREDIENTS");
    }
}
