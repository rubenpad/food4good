package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.KitchenSettings;
import com.food4good.kitchen.domain.model.NewOrder;
import com.food4good.kitchen.domain.model.PlacedOrders;
import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.ports.in.AdmitOrdersUseCase;
import com.food4good.kitchen.domain.ports.out.OrderChangesPort;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.IngredientRequest;
import com.food4good.storage.domain.ports.in.RequestIngredientsUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class AdmitOrdersService implements AdmitOrdersUseCase {

    private final RecipeRepositoryPort recipes;
    private final OrderRepositoryPort orders;
    private final RequestIngredientsUseCase requestIngredients;
    private final TransactionPort transactions;
    private final OrderChangesPort orderChanges;
    private final KitchenSettings settings;

    public AdmitOrdersService(
            RecipeRepositoryPort recipes,
            OrderRepositoryPort orders,
            RequestIngredientsUseCase requestIngredients,
            TransactionPort transactions,
            OrderChangesPort orderChanges,
            KitchenSettings settings) {
        this.recipes = recipes;
        this.orders = orders;
        this.requestIngredients = requestIngredients;
        this.transactions = transactions;
        this.orderChanges = orderChanges;
        this.settings = settings;
    }

    @Override
    public List<PlacedOrders> admit(List<Integer> quantities) {
        var available = recipes.findAll();

        return transactions.inTransaction(() -> {
            // The lock stops another instance overbooking. It comes first, so the Backlog read next
            // includes every request admitted before this group.
            orders.lockAdmission();
            var work = orders.backlogWork();
            var accepted = new ArrayList<NewOrder>();
            var outcomes = new ArrayList<PlacedOrders>();
            for (var quantity : quantities) {
                var ids = new ArrayList<UUID>();
                while (ids.size() < quantity) {
                    var recipe = drawFrom(available);
                    var preparationTime = settings.scaled(recipe.preparationTime());
                    var after = work.plus(preparationTime);
                    if (after.compareTo(settings.capacity()) > 0) {
                        break;
                    }
                    work = after;
                    var order = new NewOrder(UUID.randomUUID(), recipe, preparationTime);
                    accepted.add(order);
                    ids.add(order.id());
                }
                outcomes.add(new PlacedOrders(quantity, ids, settings.timeToClear(work)));
            }
            if (!accepted.isEmpty()) {
                orders.insertAll(accepted);
                requestIngredients.request(accepted.stream()
                        .map(order -> new IngredientRequest(order.id(), order.recipe().ingredients()))
                        .toList());
                orderChanges.changed(accepted.stream().map(NewOrder::id).toList());
            }
            return outcomes;
        });
    }

    private static Recipe drawFrom(List<Recipe> recipes) {
        return recipes.get(ThreadLocalRandom.current().nextInt(recipes.size()));
    }
}
