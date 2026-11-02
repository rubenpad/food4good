package com.food4good.kitchen.domain;

import com.food4good.storage.Ingredient;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A preparation the Kitchen can make: the Ingredients it needs, how many of each, and how long it takes. */
public record Recipe(String name, Duration preparationTime, Map<Ingredient, Integer> ingredients) {

    public Recipe {
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
