package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.KitchenSettings;
import com.food4good.kitchen.domain.model.KitchenStatus;
import com.food4good.kitchen.domain.model.OrderStatus;
import com.food4good.kitchen.domain.ports.in.GetKitchenStatusUseCase;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import java.time.Duration;

public class GetKitchenStatusService implements GetKitchenStatusUseCase {

    private final OrderRepositoryPort orders;
    private final RecipeRepositoryPort recipes;
    private final KitchenSettings settings;

    public GetKitchenStatusService(
            OrderRepositoryPort orders, RecipeRepositoryPort recipes, KitchenSettings settings) {
        this.orders = orders;
        this.recipes = recipes;
        this.settings = settings;
    }

    @Override
    public KitchenStatus status() {
        var counts = orders.countOpenByStatus();
        var work = orders.backlogWork();
        return new KitchenStatus(
                settings.cooks(),
                counts.getOrDefault(OrderStatus.WAITING_FOR_INGREDIENTS, 0),
                counts.getOrDefault(OrderStatus.READY, 0),
                counts.getOrDefault(OrderStatus.PREPARING, 0),
                settings.maxWait(),
                settings.timeToClear(work),
                estimateCapacity(work));
    }

    /** An estimate because the exact count depends on which Recipes get drawn. */
    private int estimateCapacity(Duration work) {
        var room = settings.capacity().minus(work);
        var averageMillis = recipes.findAll().stream()
                .mapToLong(recipe -> settings.scaled(recipe.preparationTime()).toMillis())
                .average();
        if (room.isNegative() || averageMillis.isEmpty()) {
            return 0;
        }
        return (int) (room.toMillis() / averageMillis.getAsDouble());
    }
}
