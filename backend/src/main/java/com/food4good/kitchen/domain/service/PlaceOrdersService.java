package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.InvalidOrderRequestException;
import com.food4good.kitchen.domain.model.NewOrder;
import com.food4good.kitchen.domain.model.PlacedOrders;
import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.ports.in.PlaceOrdersUseCase;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.IngredientRequest;
import com.food4good.storage.domain.ports.in.RequestIngredientsUseCase;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class PlaceOrdersService implements PlaceOrdersUseCase {

    /** Protects the server, not a business limit. */
    private static final int MAX_QUANTITY = 10_000;

    private final RecipeRepositoryPort recipes;
    private final OrderRepositoryPort orders;
    private final RequestIngredientsUseCase requestIngredients;
    private final TransactionPort transactions;

    public PlaceOrdersService(
            RecipeRepositoryPort recipes,
            OrderRepositoryPort orders,
            RequestIngredientsUseCase requestIngredients,
            TransactionPort transactions) {
        this.recipes = recipes;
        this.orders = orders;
        this.requestIngredients = requestIngredients;
        this.transactions = transactions;
    }

    @Override
    public PlacedOrders place(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidOrderRequestException("Quantity must be between 1 and %d".formatted(MAX_QUANTITY));
        }
        var available = recipes.findAll();
        var accepted = IntStream.range(0, quantity)
                .mapToObj(i -> new NewOrder(UUID.randomUUID(), drawFrom(available)))
                .toList();

        transactions.inTransaction(() -> {
            orders.insertAll(accepted);
            requestIngredients.request(accepted.stream()
                    .map(order -> new IngredientRequest(order.id(), order.recipe().ingredients()))
                    .toList());
        });
        return new PlacedOrders(quantity, accepted.stream().map(NewOrder::id).toList());
    }

    private static Recipe drawFrom(List<Recipe> recipes) {
        return recipes.get(ThreadLocalRandom.current().nextInt(recipes.size()));
    }
}
