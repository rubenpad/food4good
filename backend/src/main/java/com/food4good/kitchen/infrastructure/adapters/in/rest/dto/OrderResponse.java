package com.food4good.kitchen.infrastructure.adapters.in.rest.dto;

import com.food4good.kitchen.domain.model.Order;
import com.food4good.kitchen.domain.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID recipeId,
        String recipe,
        long preparationTimeSeconds,
        OrderStatus status,
        Instant acceptedAt,
        Instant readyAt,
        Instant preparingAt,
        Instant prepareUntil,
        Instant deliveredAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.recipeId(),
                order.recipeName(),
                order.preparationTime().toSeconds(),
                order.status(),
                order.acceptedAt(),
                order.readyAt(),
                order.preparingAt(),
                order.prepareUntil(),
                order.deliveredAt());
    }
}
