package com.food4good.storage.infrastructure.adapters.out.market;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

/** The Farmers Market's HTTP API. */
interface FarmersMarketClient {

    @GetExchange("/api/farmers-market/buy")
    BuyResponse buy(@RequestParam String ingredient);

    /** The spec spells this field two ways, so accept both. */
    record BuyResponse(@JsonAlias("quanitySold") Integer quantitySold) {
    }
}
