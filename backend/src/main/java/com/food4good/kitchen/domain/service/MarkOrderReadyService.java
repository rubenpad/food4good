package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.ports.in.MarkOrderReadyUseCase;
import com.food4good.kitchen.domain.ports.out.OrderChangesPort;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import java.util.List;
import java.util.UUID;

public class MarkOrderReadyService implements MarkOrderReadyUseCase {

    private final OrderRepositoryPort orders;
    private final OrderChangesPort orderChanges;

    public MarkOrderReadyService(OrderRepositoryPort orders, OrderChangesPort orderChanges) {
        this.orders = orders;
        this.orderChanges = orderChanges;
    }

    @Override
    public void markReady(UUID orderId) {
        orders.markReady(orderId);
        orderChanges.changed(List.of(orderId));
    }
}
