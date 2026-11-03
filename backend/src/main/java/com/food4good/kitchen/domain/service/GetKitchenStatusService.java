package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.KitchenSettings;
import com.food4good.kitchen.domain.model.KitchenStatus;
import com.food4good.kitchen.domain.model.OrderStatus;
import com.food4good.kitchen.domain.ports.in.GetKitchenStatusUseCase;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;

public class GetKitchenStatusService implements GetKitchenStatusUseCase {

    private final OrderRepositoryPort orders;
    private final KitchenSettings settings;

    public GetKitchenStatusService(OrderRepositoryPort orders, KitchenSettings settings) {
        this.orders = orders;
        this.settings = settings;
    }

    @Override
    public KitchenStatus status() {
        var counts = orders.countOpenByStatus();
        return new KitchenStatus(
                settings.cooks(),
                counts.getOrDefault(OrderStatus.WAITING_FOR_INGREDIENTS, 0),
                counts.getOrDefault(OrderStatus.READY, 0),
                counts.getOrDefault(OrderStatus.PREPARING, 0));
    }
}
