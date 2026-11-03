package com.food4good.kitchen.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** The outcome of a request to place Orders, and the estimated time to clear the Backlog afterwards. */
public record PlacedOrders(int requested, List<UUID> orderIds, Duration estimatedWait) {

    public int accepted() {
        return orderIds.size();
    }
}
