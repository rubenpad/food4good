package com.food4good.shared;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Spans are collected in memory instead of being sent to Honeycomb, the way the market is a stub. One
 * Cook and fast Dishes, so an Order goes all the way through in seconds. Food Storage starts with 5 of
 * each Ingredient.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(TracingApiTest.CollectSpans.class)
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=1",
    "food4good.kitchen.preparation-time-scale=0.001",
    "management.opentelemetry.tracing.export.schedule-delay=50ms",
})
class TracingApiTest extends ApiTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);
    private static final String BUY = "/api/farmers-market/buy";
    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");

    @TestConfiguration(proxyBeanMethods = false)
    static class CollectSpans {

        @Bean
        InMemorySpanExporter spans() {
            return InMemorySpanExporter.create();
        }
    }

    @RegisterExtension
    static final WireMockExtension market = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void marketUrl(DynamicPropertyRegistry registry) {
        registry.add("food4good.market.url", market::baseUrl);
    }

    @Autowired
    private InMemorySpanExporter spans;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void forgetEarlierSpans() {
        market.stubFor(get(urlPathEqualTo(BUY)).willReturn(sold(0)));
        spans.reset();
    }

    private static ResponseDefinitionBuilder sold(int quantity) {
        return okJson("{\"quantitySold\": " + quantity + "}");
    }

    /** The market answers these in turn for one Ingredient, then keeps repeating the last. */
    private void marketAnswers(String ingredient, ResponseDefinitionBuilder... answers) {
        var state = Scenario.STARTED;
        for (int i = 0; i < answers.length; i++) {
            var next = i == answers.length - 1 ? state : "answer " + (i + 1);
            market.stubFor(get(urlPathEqualTo(BUY))
                    .withQueryParam("ingredient", equalTo(ingredient))
                    .inScenario(ingredient)
                    .whenScenarioStateIs(state)
                    .willReturn(answers[i])
                    .willSetStateTo(next));
            state = next;
        }
    }

    private List<SpanData> spansOf(String order) {
        return spans.getFinishedSpanItems().stream()
                .filter(span -> order.equals(span.getAttributes().get(ORDER_ID)))
                .toList();
    }

    private SpanData only(List<SpanData> spans, String name) {
        var named = spans.stream().filter(span -> span.getName().equals(name)).toList();
        assertThat(named).as(name).hasSize(1);
        return named.getFirst();
    }

    private SpanData spanWithId(String spanId) {
        return spans.getFinishedSpanItems().stream()
                .filter(span -> span.getSpanId().equals(spanId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finished span " + spanId));
    }

    /** The span this one says caused it, which lives in another trace. */
    private SpanData causeOf(SpanData span) {
        assertThat(span.getParentSpanContext().isValid()).as("%s starts its own trace", span.getName()).isFalse();
        assertThat(span.getLinks()).as("%s links to its cause", span.getName()).hasSize(1);
        return spanWithId(span.getLinks().getFirst().getSpanContext().getSpanId());
    }

    /** Buys meat twice (nothing, then 4), then cooks and delivers. */
    private String anOrderThatHadToBuyMeat() {
        givenEveryRecipeIs(Map.of("meat", 6), 60);
        marketAnswers("meat", sold(0), sold(4));
        var order = placeOrders(1).getFirst();
        Awaitility.await().atMost(PATIENCE).until(() -> statusOf(order).equals("DELIVERED"));
        Awaitility.await().atMost(PATIENCE).until(() -> spansOf(order).stream().anyMatch(span -> span.getName().equals("Deliver")));
        return order;
    }

    @Test
    void oneOrdersWholeJourneyCanBeFoundByItsId() {
        var order = anOrderThatHadToBuyMeat();

        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(spansOf(order))
                .extracting(SpanData::getName)
                .containsExactlyInAnyOrder(
                        "AcceptOrder", "Reserve", "BuyFromMarket", "BuyFromMarket", "AddPurchase",
                        "StartCooking", "Consume", "Deliver"));
        var attempts = spansOf(order).stream().filter(span -> span.getName().equals("BuyFromMarket")).toList();
        assertThat(attempts).allSatisfy(span ->
                assertThat(span.getAttributes().get(AttributeKey.stringKey("ingredient"))).isEqualTo("meat"));
        assertThat(attempts)
                .extracting(span -> span.getAttributes().get(AttributeKey.longKey("market.attempt")),
                        span -> span.getAttributes().get(AttributeKey.longKey("market.quantity_sold")))
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 0L), org.assertj.core.groups.Tuple.tuple(2L, 4L));
        assertThat(only(spansOf(order), "AcceptOrder").getAttributes().get(AttributeKey.stringKey("recipe")))
                .isNotBlank();
    }

    @Test
    void workDoneLaterStartsItsOwnTraceAndLinksBackToWhatQueuedIt() {
        var order = anOrderThatHadToBuyMeat();
        var journey = spansOf(order);

        var reserve = only(journey, "Reserve");
        var startCooking = only(journey, "StartCooking");
        assertThat(causeOf(startCooking)).isEqualTo(only(journey, "AcceptOrder"));
        assertThat(causeOf(only(journey, "Consume"))).isEqualTo(startCooking);
        var sale = journey.stream()
                .filter(span -> span.getName().equals("BuyFromMarket"))
                .filter(span -> Long.valueOf(4).equals(span.getAttributes().get(AttributeKey.longKey("market.quantity_sold"))))
                .findFirst()
                .orElseThrow();
        assertThat(causeOf(only(journey, "AddPurchase"))).isEqualTo(sale);
        // Each attempt links to the reservation that found the shortfall.
        journey.stream()
                .filter(span -> span.getName().equals("BuyFromMarket"))
                .forEach(attempt -> assertThat(causeOf(attempt)).isEqualTo(reserve));
        // The reservation was queued with every Ingredient Request of its admission group.
        assertThat(causeOf(reserve).getName()).isEqualTo("RequestIngredients");
        assertThat(causeOf(only(journey, "Deliver"))).isEqualTo(only(journey, "AcceptOrder"));
    }

    @Test
    void acceptingAnOrderIsPartOfTheAdmissionAndLinksToTheRequestThatAskedForIt() {
        givenEveryRecipeIs(Map.of("rice", 1), 60);

        var orders = placeOrders(2);

        Awaitility.await().atMost(PATIENCE).until(() -> spans.getFinishedSpanItems().stream()
                .anyMatch(span -> span.getName().equals("PlaceOrders")));
        var place = only(spans.getFinishedSpanItems(), "PlaceOrders");
        assertThat(place.getAttributes().get(AttributeKey.longKey("admission.requested"))).isEqualTo(2L);
        assertThat(place.getAttributes().get(AttributeKey.longKey("admission.accepted"))).isEqualTo(2L);
        // Placing Orders is part of the HTTP request's trace.
        assertThat(spanWithId(place.getParentSpanId()).getKind()).isEqualTo(SpanKind.SERVER);
        for (var order : orders) {
            var accept = only(spansOf(order), "AcceptOrder");
            assertThat(spanWithId(accept.getParentSpanId()).getName()).isEqualTo("AdmitOrders");
            assertThat(accept.getLinks()).extracting(LinkData::getSpanContext).extracting(SpanContext::getSpanId)
                    .containsExactly(place.getSpanId());
        }
    }

    @Test
    void everyMarketCallIsASpanInsideItsAttempt() {
        var order = anOrderThatHadToBuyMeat();

        for (var attempt : spansOf(order).stream().filter(span -> span.getName().equals("BuyFromMarket")).toList()) {
            assertThat(spans.getFinishedSpanItems())
                    .filteredOn(span -> span.getParentSpanId().equals(attempt.getSpanId()))
                    .filteredOn(span -> span.getKind() == SpanKind.CLIENT)
                    .hasSize(1);
        }
    }

    @Test
    void marketCallsAreCountedBySoldNothingOrError() {
        givenEveryRecipeIs(Map.of("cheese", 6), 60);
        marketAnswers("cheese", sold(0), serverError(), sold(3));
        var before = marketCalls();

        var order = placeOrders(1).getFirst();
        Awaitility.await().atMost(PATIENCE).until(() -> statusOf(order).equals("DELIVERED"));

        // Counters keep counting for as long as the app runs, so only the change is this test's.
        var after = marketCalls();
        for (var result : List.of("zero", "error", "sold")) {
            assertThat(after.get(result) - before.get(result)).as(result).isEqualTo(1);
        }
    }

    private Map<String, Double> marketCalls() {
        return Map.of(
                "zero", meters.get("market.calls").tag("result", "zero").counter().count(),
                "error", meters.get("market.calls").tag("result", "error").counter().count(),
                "sold", meters.get("market.calls").tag("result", "sold").counter().count());
    }

    @Test
    void theKitchenAndTheQueuesAreMeasured() {
        givenEveryRecipeIs(Map.of("meat", 200_000), 60);

        placeOrders(1);

        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
            assertThat(meters.get("market.jobs.pending").gauge().value()).isEqualTo(1);
            assertThat(meters.get("storage.commands.pending").gauge().value()).isZero();
            assertThat(meters.get("kitchen.backlog.seconds").gauge().value()).isPositive();
            assertThat(meters.get("kitchen.cooks.busy").gauge().value()).isZero();
        });
    }

    @Test
    void workThatGivesUpIsCounted() {
        givenEveryRecipeIs(Map.of("rice", 2), 10);
        failWrites("reservation_line", "UPDATE", "true");

        var before = meters.get("jobs.failed").tag("queue", "storage").counter().count();

        placeOrders(1);

        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(meters.get("jobs.failed").tag("queue", "storage").counter().count() - before).isEqualTo(1));
        stopFailing("reservation_line");
    }

    @Test
    void logsAreJsonAndSayWhichTraceTheyBelongTo(CapturedOutput output) {
        givenEveryRecipeIs(Map.of("rice", 2), 10);
        failWrites("reservation_line", "UPDATE", "true");

        var order = placeOrders(1).getFirst();

        Awaitility.await().atMost(PATIENCE).until(() -> output.getOut().lines().anyMatch(line -> line.contains("failed")
                && line.contains("Reserve")));
        stopFailing("reservation_line");
        var line = output.getOut().lines()
                .filter(candidate -> candidate.contains("failed") && candidate.contains("Reserve"))
                .findFirst()
                .orElseThrow();
        Map<String, Object> entry = JsonPath.read(line, "$");
        var reserve = spansOf(order).stream().filter(span -> span.getName().equals("Reserve")).findFirst().orElseThrow();
        assertThat(entry).containsEntry("traceId", reserve.getTraceId());
        assertThat(entry).containsKey("spanId");
    }

    @Test
    void withoutAHoneycombKeyNothingIsExported() {
        assertThat(context.getBeansOfType(SpanExporter.class).values()).containsExactly(spans);
        assertThat(context.getBeanNamesForType(io.opentelemetry.sdk.logs.export.LogRecordExporter.class)).isEmpty();
        assertThat(context.getBeansOfType(MeterRegistry.class).values())
                .extracting(registry -> registry.getClass().getSimpleName())
                .doesNotContain("OtlpMeterRegistry");
    }
}
