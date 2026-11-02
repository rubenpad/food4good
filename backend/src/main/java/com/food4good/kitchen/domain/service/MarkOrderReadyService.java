package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.ports.in.MarkOrderReadyUseCase;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import java.util.UUID;

public class MarkOrderReadyService implements MarkOrderReadyUseCase {

    private final OrderRepositoryPort orders;

    public MarkOrderReadyService(OrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Override
    public void markReady(UUID orderId) {
        orders.markReady(orderId);
    }
}
