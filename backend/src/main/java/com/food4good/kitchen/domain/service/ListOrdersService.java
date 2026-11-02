package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.Order;
import com.food4good.kitchen.domain.model.OrderCursor;
import com.food4good.kitchen.domain.model.OrderPage;
import com.food4good.kitchen.domain.model.OrderQuery;
import com.food4good.kitchen.domain.ports.in.ListOrdersUseCase;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;

public class ListOrdersService implements ListOrdersUseCase {

    private final OrderRepositoryPort orders;

    public ListOrdersService(OrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Override
    public OrderPage list(OrderQuery query) {
        var found = orders.find(query);
        if (found.size() <= query.limit()) {
            return new OrderPage(found, null);
        }
        var page = found.subList(0, query.limit());
        Order last = page.getLast();
        return new OrderPage(page, new OrderCursor(last.acceptedAt(), last.id()));
    }
}
