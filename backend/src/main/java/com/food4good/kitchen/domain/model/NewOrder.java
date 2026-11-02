package com.food4good.kitchen.domain.model;

import java.util.UUID;

/** An Order being accepted, with the Recipe drawn for it. */
public record NewOrder(UUID id, Recipe recipe) {
}
