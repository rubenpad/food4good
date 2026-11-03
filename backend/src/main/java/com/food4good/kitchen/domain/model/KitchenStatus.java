package com.food4good.kitchen.domain.model;

import java.time.Duration;

/** The Kitchen right now: its Cooks, the Orders not yet delivered, and how much more it can take on. */
public record KitchenStatus(
        int cooks,
        int waitingForIngredients,
        int ready,
        int preparing,
        Duration maxWait,
        Duration backlog,
        int estimatedCapacity) {

    public int busyCooks() {
        return preparing;
    }
}
