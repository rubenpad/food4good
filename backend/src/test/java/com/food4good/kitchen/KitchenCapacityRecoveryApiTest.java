package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/** One Cook and a tiny Maximum wait: room frees up as Dishes are delivered. */
@TestPropertySource(properties = {
    "food4good.kitchen.cooks=1",
    "food4good.kitchen.max-wait=2s",
    "food4good.kitchen.preparation-time-scale=0.01",
    "spring.flyway.placeholders.initial_stock=1000",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KitchenCapacityRecoveryApiTest extends ApiTest {

    private int accepted(int quantity) {
        var response = send(HttpMethod.POST, "/orders", Map.of("quantity", quantity));
        return JsonPath.read(response.getBody(), "$.accepted");
    }

    @Test
    void aFullKitchenAcceptsOrdersAgainOnceDishesAreDelivered() {
        givenEveryRecipeIs(Map.of("rice", 1), 200);

        assertThat(accepted(5)).isEqualTo(1);
        assertThat(accepted(1)).isZero();

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> accepted(1) == 1);
    }
}
