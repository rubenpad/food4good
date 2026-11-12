package com.food4good.storage.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import java.util.UUID;

/**
 * Buying what one Order is still short of one Ingredient. An attempt is one call to the Farmers
 * Market. {@code cause} is the trace context of the reservation that found the shortfall.
 */
public record MarketJob(
        long id, UUID orderId, Ingredient ingredient, int remaining, int attempt, int zeroStreak, int errorAttempts,
        String cause) {
}
