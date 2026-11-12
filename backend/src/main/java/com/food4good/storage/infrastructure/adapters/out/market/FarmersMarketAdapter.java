package com.food4good.storage.infrastructure.adapters.out.market;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.ports.out.FarmersMarketPort;
import com.food4good.storage.infrastructure.configuration.MarketProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Retrying is the market job's business, so a failed call simply sold nothing. */
@Component
class FarmersMarketAdapter implements FarmersMarketPort {

    private static final Logger log = LoggerFactory.getLogger(FarmersMarketAdapter.class);

    private final FarmersMarketClient client;
    private final Counter sold;
    private final Counter soldNothing;
    private final Counter failed;

    FarmersMarketAdapter(RestClient.Builder builder, MarketProperties properties, MeterRegistry meters) {
        this.sold = calls(meters, "sold");
        this.soldNothing = calls(meters, "zero");
        this.failed = calls(meters, "error");
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        var restClient = builder.baseUrl(properties.url()).requestFactory(requestFactory).build();
        this.client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(FarmersMarketClient.class);
    }

    private static Counter calls(MeterRegistry meters, String result) {
        return Counter.builder("market.calls")
                .description("Calls to the Farmers Market, by what came of them")
                .tag("result", result)
                .register(meters);
    }

    @Override
    public int buy(Ingredient ingredient) {
        int quantity;
        try {
            var response = client.buy(ingredient.id());
            var reported = response == null ? null : response.quantitySold();
            quantity = reported == null ? 0 : Math.max(0, reported);
        } catch (RestClientException e) {
            log.debug("Buying {} failed; counting it as nothing sold", ingredient.id(), e);
            failed.increment();
            return 0;
        }
        (quantity > 0 ? sold : soldNothing).increment();
        return quantity;
    }
}
