package com.food4good.kitchen.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A preparation the Kitchen can make: the Ingredients it needs, how many of each, and how long it
 * takes. Names have no surrounding whitespace and Ingredients are ordered by name.
 */
public record Recipe(UUID id, String name, Duration preparationTime, Map<Ingredient, Integer> ingredients) {

    public Recipe {
        if (id == null) {
            throw new InvalidRecipeException("Every Recipe needs an id");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidRecipeException("Every Recipe needs a name");
        }
        name = name.strip();
        if (preparationTime == null || preparationTime.isNegative() || preparationTime.isZero()) {
            throw new InvalidRecipeException("Recipe '%s' needs a positive preparation time".formatted(name));
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new InvalidRecipeException("Recipe '%s' needs at least one Ingredient".formatted(name));
        }
        var ordered = new LinkedHashMap<Ingredient, Integer>();
        for (var entry : ingredients.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Ingredient::id)))
                .toList()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new InvalidRecipeException(
                        "Recipe '%s' needs a positive quantity of %s".formatted(name, entry.getKey().id()));
            }
            ordered.put(entry.getKey(), entry.getValue());
        }
        ingredients = Collections.unmodifiableMap(ordered);
    }

    public static Recipe of(UUID id, RecipeDetails details) {
        return new Recipe(id, details.name(), details.preparationTime(), details.ingredients());
    }
}
