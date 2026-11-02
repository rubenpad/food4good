package com.food4good.kitchen.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A Manager's request for one Dish, keeping the Recipe's name and preparation time as they were when accepted. */
public record Order(
        UUID id,
        UUID recipeId,
        String recipeName,
        Duration preparationTime,
        OrderStatus status,
        Instant acceptedAt,
        Instant readyAt,
        Instant preparingAt,
        Instant deliveredAt) {
}
