package com.food4good.storage;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** No Cooks, so what Food Storage bought and reserved stays visible. Food Storage starts with 5 of each. */
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=0",
    "food4good.market.read-timeout=300ms",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarketApiTest extends ApiTest {

    private static final String BUY = "/api/farmers-market/buy";
    private static final Duration PATIENCE = Duration.ofSeconds(30);

    @RegisterExtension
    static final WireMockExtension market = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void marketUrl(DynamicPropertyRegistry registry) {
        registry.add("food4good.market.url", market::baseUrl);
    }

    @BeforeEach
    void marketSellsNothingUnlessToldOtherwise() {
        market.stubFor(get(urlPathEqualTo(BUY)).willReturn(sold(0)));
    }

    private static ResponseDefinitionBuilder sold(int quantity) {
        return okJson("{\"quantitySold\": " + quantity + "}");
    }

    private static MappingBuilder buying(String ingredient) {
        return get(urlPathEqualTo(BUY)).withQueryParam("ingredient", equalTo(ingredient));
    }

    /** The market answers these in turn for one Ingredient, then keeps repeating the last. */
    private void marketAnswers(String ingredient, ResponseDefinitionBuilder... answers) {
        var state = Scenario.STARTED;
        for (int i = 0; i < answers.length; i++) {
            var next = i == answers.length - 1 ? state : "answer " + (i + 1);
            market.stubFor(buying(ingredient)
                    .inScenario(ingredient)
                    .whenScenarioStateIs(state)
                    .willReturn(answers[i])
                    .willSetStateTo(next));
            state = next;
        }
    }

    private int callsFor(String ingredient) {
        return market.findAll(getRequestedFor(urlPathEqualTo(BUY)).withQueryParam("ingredient", equalTo(ingredient)))
                .size();
    }

    private List<Map<String, Object>> purchases() {
        return getJson("/purchases?limit=200").read("$.items");
    }

    private void awaitReady(String order) {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(statusOf(order)).isEqualTo("READY"));
    }

    @Test
    void buysTheShortfallKeepsTheSurplusAvailableAndStopsCalling() throws Exception {
        givenEveryRecipeIs(Map.of("meat", 6), 60);
        marketAnswers("meat", sold(4));

        var order = placeOrders(1).getFirst();

        awaitReady(order);
        Awaitility.await().atMost(PATIENCE).untilAsserted(
                () -> assertThat(stockOf("meat")).containsExactly(9, 6, 3));
        assertThat(purchases()).singleElement().satisfies(purchase -> {
            assertThat(purchase).containsEntry("ingredient", "meat");
            assertThat(purchase).containsEntry("quantity", 4);
            assertThat(purchase.get("purchasedAt")).isNotNull();
        });
        Thread.sleep(500);
        assertThat(callsFor("meat")).isEqualTo(1);
    }

    @Test
    void anOrderWithStockNeverCallsTheMarket() throws Exception {
        givenEveryRecipeIs(Map.of("rice", 2), 60);

        awaitReady(placeOrders(1).getFirst());

        Thread.sleep(500);
        assertThat(market.getAllServeEvents()).isEmpty();
        assertThat(purchases()).isEmpty();
    }

    @Test
    void keepsTryingWhenTheMarketSellsNothingAndOnlyRecordsActualSales() {
        givenEveryRecipeIs(Map.of("cheese", 6), 60);
        marketAnswers("cheese", sold(0), sold(0), sold(4));

        var order = placeOrders(1).getFirst();

        awaitReady(order);
        assertThat(callsFor("cheese")).isEqualTo(3);
        assertThat(purchases()).singleElement().satisfies(purchase -> assertThat(purchase).containsEntry("quantity", 4));
    }

    @Test
    void waitsLongerAfterEachZeroSale() {
        givenEveryRecipeIs(Map.of("cheese", 6), 60);
        marketAnswers("cheese", sold(0), sold(0), sold(0), sold(1));

        awaitReady(placeOrders(1).getFirst());

        var times = market.getAllServeEvents().stream()
                .map(event -> event.getRequest().getLoggedDate().getTime())
                .sorted()
                .toList();
        assertThat(times).hasSize(4);
        var firstGap = times.get(1) - times.get(0);
        var lastGap = times.get(3) - times.get(2);
        // 250 ms then 1 s, each ±20 %.
        assertThat(firstGap).isGreaterThanOrEqualTo(180);
        assertThat(lastGap).isGreaterThan(firstGap);
    }

    @Test
    void aSeriesOfSmallSalesAddsUpToTheShortfall() {
        givenEveryRecipeIs(Map.of("potato", 8), 60);
        marketAnswers("potato", sold(1), sold(1), sold(5));

        var order = placeOrders(1).getFirst();

        awaitReady(order);
        assertThat(callsFor("potato")).isEqualTo(3);
        Awaitility.await().atMost(PATIENCE).untilAsserted(
                () -> assertThat(stockOf("potato")).containsExactly(12, 8, 4));
        assertThat(purchases()).hasSize(3);
    }

    @Test
    void anOrderShortOfTwoIngredientsWaitsUntilBothAreBought() {
        givenEveryRecipeIs(Map.of("lemon", 6, "onion", 7), 60);
        marketAnswers("lemon", sold(1));
        marketAnswers("onion", sold(0));

        var order = placeOrders(1).getFirst();

        Awaitility.await().atMost(PATIENCE).until(() -> stockOf("lemon").get(1) == 6);
        assertThat(statusOf(order)).isEqualTo("WAITING_FOR_INGREDIENTS");

        market.resetAll();
        market.stubFor(buying("onion").willReturn(sold(2)));

        awaitReady(order);
        assertThat(stockOf("onion")).containsExactly(7, 7, 0);
    }

    @Test
    void aMarketTimeoutCountsAsNothingSoldAndIsRetried() {
        givenEveryRecipeIs(Map.of("meat", 6), 60);
        marketAnswers("meat", sold(5).withFixedDelay(1_000), sold(1));

        var order = placeOrders(1).getFirst();

        awaitReady(order);
        assertThat(callsFor("meat")).isEqualTo(2);
        assertThat(purchases()).singleElement().satisfies(purchase -> assertThat(purchase).containsEntry("quantity", 1));
    }

    @Test
    void marketErrorsAndNonsenseCountAsNothingSold() {
        givenEveryRecipeIs(Map.of("meat", 6), 60);
        marketAnswers(
                "meat",
                aResponse().withStatus(500),
                okJson("{\"quantitySold\": \"lots\"}"),
                okJson("{}"),
                aResponse().withStatus(200).withBody("not json"),
                sold(-3),
                sold(2));

        var order = placeOrders(1).getFirst();

        awaitReady(order);
        assertThat(purchases()).singleElement().satisfies(purchase -> assertThat(purchase).containsEntry("quantity", 2));
        assertStockRulesHold();
    }

    @Test
    void aMarketThatKeepsFailingNeverMakesAnOrderGiveUp() {
        givenEveryRecipeIs(Map.of("meat", 6), 60);
        marketAnswers(
                "meat",
                aResponse().withStatus(500),
                aResponse().withStatus(503),
                aResponse().withStatus(200).withBody("not json"),
                aResponse().withStatus(500),
                aResponse().withStatus(200).withBody("not json"),
                aResponse().withStatus(404),
                sold(1));

        awaitReady(placeOrders(1).getFirst());

        assertThat(callsFor("meat")).isEqualTo(7);
    }

    @Test
    void anEmptyMarketDelaysOnlyTheOrdersThatNeedIt() {
        givenEveryRecipeIs(Map.of("meat", 100), 60);
        market.stubFor(buying("meat").willReturn(sold(0).withFixedDelay(250)));
        var stuck = placeOrders(30);

        givenEveryRecipeIs(Map.of("rice", 1), 60);
        var started = System.nanoTime();
        var order = placeOrders(1).getFirst();
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(statusOf(order)).isEqualTo("READY"));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        assertThat(statusOf(stuck.getFirst())).isEqualTo("WAITING_FOR_INGREDIENTS");
    }

    @Test
    void manyShortOrdersAllCompleteWithStockAddingUp() {
        givenEveryRecipeIs(Map.of("chicken", 3), 60);
        market.stubFor(buying("chicken").willReturn(sold(2)));

        var orders = placeOrders(20);

        Awaitility.await().atMost(PATIENCE).pollInterval(Duration.ofMillis(200)).untilAsserted(
                () -> orders.forEach(id -> assertThat(statusOf(id)).isEqualTo("READY")));
        var bought = purchases().stream().mapToInt(purchase -> (Integer) purchase.get("quantity")).sum();
        Awaitility.await().atMost(PATIENCE).untilAsserted(
                () -> assertThat(stockOf("chicken")).containsExactly(5 + bought, 60, 5 + bought - 60));
        assertStockRulesHold();
    }

    @Test
    void purchaseHistoryIsNewestFirstAndPages() {
        givenEveryRecipeIs(Map.of("tomato", 8), 60);
        marketAnswers("tomato", sold(1));
        awaitReady(placeOrders(1).getFirst());

        var seen = new ArrayList<Integer>();
        String cursor = null;
        do {
            var page = getJson("/purchases?limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
            List<Integer> ids = page.read("$.items[*].id");
            assertThat(ids).hasSizeLessThanOrEqualTo(1);
            seen.addAll(ids);
            cursor = page.read("$.nextCursor");
        } while (cursor != null);

        assertThat(seen).hasSize(3).doesNotHaveDuplicates().isSortedAccordingTo((a, b) -> b - a);
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=201", "limit=lots", "cursor=not-a-cursor"})
    void refusesInvalidPurchaseHistoryQueries(String query) {
        var response = api().get().uri("/purchases?" + query).retrieve()
                .onStatus(status -> true, (request, reply) -> {
                })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) JsonPath.read(response.getBody(), "$.detail")).isNotBlank();
    }
}
