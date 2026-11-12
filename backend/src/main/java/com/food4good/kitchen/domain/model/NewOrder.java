package com.food4good.kitchen.domain.model;

import java.time.Duration;
import java.util.UUID;

/**
 * An Order being accepted, with the Recipe drawn for it and how long it will take to prepare.
 * {@code cause} is the trace context of its acceptance.
 */
public record NewOrder(UUID id, Recipe recipe, Duration preparationTime, String cause) {
}
