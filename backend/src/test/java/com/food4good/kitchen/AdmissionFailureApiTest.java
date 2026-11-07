package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

class AdmissionFailureApiTest extends ApiTest {

    @Test
    void aFailedAdmissionFailsEveryRequestInItsGroupAndCreatesNoOrders() throws Exception {
        failWrites("orders", "INSERT", "true");

        var statuses = new ArrayList<HttpStatusCode>();
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Callable<HttpStatusCode>> requests = IntStream.range(0, 20)
                    .<Callable<HttpStatusCode>>mapToObj(i -> () ->
                            send(HttpMethod.POST, "/orders", Map.of("quantity", 3)).getStatusCode())
                    .toList();
            for (var future : executor.invokeAll(requests)) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).allSatisfy(status -> assertThat(status.is5xxServerError()).isTrue());
        List<?> orders = getJson("/orders").read("$.items");
        assertThat(orders).isEmpty();

        stopFailing("orders");
        assertThat(placeOrders(2)).hasSize(2);
    }
}
