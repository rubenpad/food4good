package com.food4good.shared;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * A stand-in for Honeycomb receives whatever the app exports once {@code HONEYCOMB_API_KEY} is set.
 * The key is read before the app starts, so it is a system property here, cleared again afterwards so
 * later test classes in this JVM export nothing, and the app is closed so it stops exporting too.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "management.opentelemetry.tracing.export.schedule-delay=100ms",
    "management.opentelemetry.logging.export.schedule-delay=100ms",
    "management.otlp.metrics.export.step=1s",
})
class HoneycombExportTest extends ApiTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);
    private static final WireMockServer honeycomb = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        honeycomb.start();
        honeycomb.stubFor(post(urlPathEqualTo("/v1/traces")).willReturn(aResponse().withStatus(200)));
        honeycomb.stubFor(post(urlPathEqualTo("/v1/metrics")).willReturn(aResponse().withStatus(200)));
        honeycomb.stubFor(post(urlPathEqualTo("/v1/logs")).willReturn(aResponse().withStatus(200)));
        System.setProperty("HONEYCOMB_API_KEY", "test-key");
        System.setProperty("HONEYCOMB_ENDPOINT", honeycomb.baseUrl() + "/");
    }

    @AfterAll
    static void stopExporting() {
        System.clearProperty("HONEYCOMB_API_KEY");
        System.clearProperty("HONEYCOMB_ENDPOINT");
        honeycomb.stop();
    }

    private static RequestPatternBuilder sentTo(String path) {
        return postRequestedFor(urlPathEqualTo(path)).withHeader("x-honeycomb-team", equalTo("test-key"));
    }

    private static boolean anyBodyContains(String path, String text) {
        return honeycomb.findAll(sentTo(path)).stream()
                .anyMatch(request -> new String(request.getBody(), StandardCharsets.ISO_8859_1).contains(text));
    }

    @Test
    void tracesGoToHoneycombWithTheKey() {
        givenEveryRecipeIs(Map.of("rice", 1), 60);

        placeOrders(1);

        Awaitility.await().atMost(PATIENCE).until(() -> anyBodyContains("/v1/traces", "AcceptOrder"));
    }

    @Test
    void metricsGoToTheirOwnDataset() {
        Awaitility.await().atMost(PATIENCE).until(() -> anyBodyContains("/v1/metrics", "kitchen.backlog.seconds"));
        assertThat(honeycomb.findAll(sentTo("/v1/metrics")))
                .allSatisfy(request -> assertThat(request.getHeader("x-honeycomb-dataset")).isEqualTo("food4good-metrics"));
    }

    @Test
    void logsGoToHoneycombWithTheKey() {
        Awaitility.await().atMost(PATIENCE).until(() -> !honeycomb.findAll(sentTo("/v1/logs")).isEmpty());
    }
}
