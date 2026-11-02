package com.food4good.storage.domain.ports.in;

import java.util.List;

/**
 * Records Ingredient Requests and queues their reservation. Runs in the caller's transaction, so a
 * request exists if and only if its Order does.
 */
public interface RequestIngredientsUseCase {

    void request(List<IngredientRequest> requests);
}
