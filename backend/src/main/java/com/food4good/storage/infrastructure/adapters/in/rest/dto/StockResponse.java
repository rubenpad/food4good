package com.food4good.storage.infrastructure.adapters.in.rest.dto;

import com.food4good.storage.domain.model.IngredientStock;

public record StockResponse(String ingredient, int onHand, int reserved, int available) {

    public static StockResponse from(IngredientStock stock) {
        return new StockResponse(stock.ingredient().id(), stock.onHand(), stock.reserved(), stock.available());
    }
}
