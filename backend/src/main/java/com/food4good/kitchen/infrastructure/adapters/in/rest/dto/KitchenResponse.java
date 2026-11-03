package com.food4good.kitchen.infrastructure.adapters.in.rest.dto;

import com.food4good.kitchen.domain.model.KitchenStatus;

public record KitchenResponse(
        int cooks, int busyCooks, int ordersWaitingForIngredients, int ordersReady, int ordersPreparing) {

    public static KitchenResponse from(KitchenStatus status) {
        return new KitchenResponse(
                status.cooks(),
                status.busyCooks(),
                status.waitingForIngredients(),
                status.ready(),
                status.preparing());
    }
}
