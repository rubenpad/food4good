package com.food4good.kitchen.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A preparation the Kitchen can make: the Ingredients it needs, how many of each, and how long it takes. */
public record Recipe(UUID id, String name, Duration preparationTime, Map<Ingredient, Integer> ingredients) {

    public Recipe {
        if (id == null) {
            throw new IllegalArgumentException("Every Recipe needs an id");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Every Recipe needs a name");
        }
        if (preparationTime == null || preparationTime.isNegative() || preparationTime.isZero()) {
            throw new IllegalArgumentException("Recipe '%s' needs a positive preparation time".formatted(name));
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("Recipe '%s' needs at least one Ingredient".formatted(name));
        }
        ingredients.forEach((ingredient, quantity) -> {
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException(
                        "Recipe '%s' needs a positive quantity of %s".formatted(name, ingredient.id()));
            }
        });
        ingredients = Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
    }
}
