package com.food4good.kitchen.domain.model;

import java.util.UUID;

public class RecipeNotFoundException extends RuntimeException {

    public RecipeNotFoundException(UUID id) {
        super("No Recipe with id %s".formatted(id));
    }
}
