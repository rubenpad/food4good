package com.food4good.kitchen.infrastructure.adapters.in.rest.dto;

import com.food4good.kitchen.domain.model.Recipe;
import java.util.List;
import java.util.UUID;

public record RecipeResponse(UUID id, String name, long preparationTimeSeconds, List<IngredientQuantity> ingredients) {

    public static RecipeResponse from(Recipe recipe) {
        return new RecipeResponse(
                recipe.id(),
                recipe.name(),
                recipe.preparationTime().toSeconds(),
                recipe.ingredients().entrySet().stream()
                        .map(entry -> new IngredientQuantity(entry.getKey().id(), entry.getValue()))
                        .toList());
    }

    public record IngredientQuantity(String ingredient, int quantity) {
    }
}
