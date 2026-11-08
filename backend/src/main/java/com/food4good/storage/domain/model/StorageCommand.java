package com.food4good.storage.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.ports.in.IngredientRequest;
import java.util.List;
import java.util.UUID;

/**
 * A queued change to Food Storage, applied by the single stock writer in the order it was queued.
 * {@code cause} is the trace context of whatever queued it.
 */
public sealed interface StorageCommand {

    long id();

    String cause();

    /** Every Ingredient Request from one group of accepted Orders, stored as a single row. */
    record RequestIngredients(long id, List<IngredientRequest> requests, String cause) implements StorageCommand {
    }

    record Reserve(long id, UUID orderId, String cause) implements StorageCommand {
    }

    record Consume(long id, UUID orderId, String cause) implements StorageCommand {
    }

    record AddPurchase(
            long id, long marketJobId, int attempt, UUID orderId, Ingredient ingredient, int quantity, String cause)
            implements StorageCommand {
    }
}
