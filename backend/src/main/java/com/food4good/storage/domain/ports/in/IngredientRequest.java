package com.food4good.storage.domain.ports.in;

import com.food4good.shared.domain.model.Ingredient;
import java.util.Map;
import java.util.UUID;

/** The Kitchen asking for every Ingredient one Order's Recipe needs. */
public record IngredientRequest(UUID orderId, Map<Ingredient, Integer> quantities) {
}
