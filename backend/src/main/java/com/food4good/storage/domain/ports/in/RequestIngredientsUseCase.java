package com.food4good.storage.domain.ports.in;

import java.util.List;

/**
 * Queues Ingredient Requests as one durable command, which the stock writer expands into Reservation
 * lines. Runs in the caller's transaction, so a request exists if and only if its Order does.
 */
public interface RequestIngredientsUseCase {

    void request(List<IngredientRequest> requests);
}
