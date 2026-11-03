package com.food4good.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "food4good.kitchen.cooks=0",
    "food4good.market.max-concurrent-calls=3",
})
class MarketConcurrencyApiTest extends ApiTest {

    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static final AtomicInteger mostInFlight = new AtomicInteger();

    /** A slow market that sells nothing and counts how many calls it is serving at once. */
    private static final HttpServer market = startMarket();

    private static HttpServer startMarket() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/api/farmers-market/buy", exchange -> {
                calls.incrementAndGet();
                mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(100);
                    var body = "{\"quantitySold\": 0}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void marketUrl(DynamicPropertyRegistry registry) {
        registry.add("food4good.market.url", () -> "http://localhost:" + market.getAddress().getPort());
    }

    @AfterAll
    static void stopMarket() {
        market.stop(0);
    }

    @Test
    void neverMoreConcurrentMarketCallsThanTheLimit() {
        givenEveryRecipeIs(Map.of("meat", 6), 60);

        placeOrders(12);

        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> calls.get() >= 30);
        assertThat(mostInFlight.get()).isEqualTo(3);
    }
}
