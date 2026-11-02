package com.food4good.kitchen.domain.model;

/** The Kitchen right now: its Cooks, and the Orders not yet delivered. */
public record KitchenStatus(int cooks, int waitingForIngredients, int ready, int preparing) {

    public int busyCooks() {
        return preparing;
    }
}
