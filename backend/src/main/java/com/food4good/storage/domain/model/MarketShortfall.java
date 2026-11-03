package com.food4good.storage.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import java.util.UUID;

/** What an Order couldn't reserve of one Ingredient and must buy. */
public record MarketShortfall(UUID orderId, Ingredient ingredient, int units) {
}
