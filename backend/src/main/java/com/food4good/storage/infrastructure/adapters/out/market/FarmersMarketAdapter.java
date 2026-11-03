package com.food4good.storage.infrastructure.adapters.out.market;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.ports.out.FarmersMarketPort;
import com.food4good.storage.infrastructure.configuration.MarketProperties;
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

    FarmersMarketAdapter(RestClient.Builder builder, MarketProperties properties) {
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        var restClient = builder.baseUrl(properties.url()).requestFactory(requestFactory).build();
        this.client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(FarmersMarketClient.class);
    }

    @Override
    public int buy(Ingredient ingredient) {
        try {
            var response = client.buy(ingredient.id());
            var sold = response == null ? null : response.quantitySold();
            return sold == null ? 0 : Math.max(0, sold);
        } catch (RestClientException e) {
            log.debug("Buying {} failed; counting it as nothing sold", ingredient.id(), e);
            return 0;
        }
    }
}
