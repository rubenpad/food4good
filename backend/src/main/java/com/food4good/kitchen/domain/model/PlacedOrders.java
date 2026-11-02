package com.food4good.kitchen.domain.model;

import java.util.List;
import java.util.UUID;

/** The outcome of a request to place Orders. */
public record PlacedOrders(int requested, List<UUID> orderIds) {

    public int accepted() {
        return orderIds.size();
    }
}
