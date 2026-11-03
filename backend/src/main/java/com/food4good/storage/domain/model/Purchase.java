package com.food4good.storage.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import java.time.Instant;

/** A successful buy from the Farmers Market. */
public record Purchase(long id, Ingredient ingredient, int quantity, Instant purchasedAt) {
}
