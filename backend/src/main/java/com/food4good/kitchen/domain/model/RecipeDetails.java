package com.food4good.kitchen.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import java.time.Duration;
import java.util.Map;

/** What the Manager supplies to create or edit a Recipe. {@link Recipe} decides whether it is valid. */
public record RecipeDetails(String name, Duration preparationTime, Map<Ingredient, Integer> ingredients) {
}
