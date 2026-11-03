package com.food4good.storage;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** The whole flow: Orders that exceed the starting stock are bought for, cooked and delivered. */
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=3",
    "food4good.kitchen.preparation-time-scale=0.01",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BuyingToDeliveryApiTest extends ApiTest {

    @RegisterExtension
    static final WireMockExtension market = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void marketUrl(DynamicPropertyRegistry registry) {
        registry.add("food4good.market.url", market::baseUrl);
    }

    @Test
    void ordersBeyondTheStartingStockAreBoughtForCookedAndDelivered() {
        market.stubFor(get(urlPathEqualTo("/api/farmers-market/buy")).willReturn(okJson("{\"quantitySold\": 3}")));
        givenEveryRecipeIs(Map.of("cheese", 2, "rice", 1), 10);

        var orders = placeOrders(20);

        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).untilAsserted(
                () -> orders.forEach(id -> assertThat(statusOf(id)).isEqualTo("DELIVERED")));
        List<Map<String, Object>> purchases = getJson("/purchases?limit=200").read("$.items");
        var cheeseBought = purchases.stream()
                .filter(purchase -> purchase.get("ingredient").equals("cheese"))
                .mapToInt(purchase -> (Integer) purchase.get("quantity"))
                .sum();
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(stockOf("cheese")).containsExactly(5 + cheeseBought - 40, 0, 5 + cheeseBought - 40);
            assertStockRulesHold();
        });
    }
}
