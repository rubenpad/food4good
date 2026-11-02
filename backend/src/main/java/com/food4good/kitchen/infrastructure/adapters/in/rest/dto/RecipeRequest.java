package com.food4good.kitchen.infrastructure.adapters.in.rest.dto;

import com.food4good.kitchen.domain.model.InvalidRecipeException;
import com.food4good.kitchen.domain.model.RecipeDetails;
import com.food4good.shared.domain.model.Ingredient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RecipeRequest(String name, Integer preparationTimeSeconds, List<IngredientQuantity> ingredients) {

    public RecipeDetails toDetails() {
        return new RecipeDetails(
                name,
                preparationTimeSeconds == null ? null : Duration.ofSeconds(preparationTimeSeconds),
                ingredients == null ? null : toIngredients());
    }

    private Map<Ingredient, Integer> toIngredients() {
        var quantities = new LinkedHashMap<Ingredient, Integer>();
        for (var item : ingredients) {
            if (item == null || item.ingredient() == null) {
                throw new InvalidRecipeException("Choose an Ingredient for every row of the Recipe");
            }
            var ingredient = Ingredient.findById(item.ingredient())
                    .orElseThrow(() -> new InvalidRecipeException("Unknown Ingredient '%s'".formatted(item.ingredient())));
            if (quantities.containsKey(ingredient)) {
                throw new InvalidRecipeException("Ingredient '%s' is listed twice".formatted(ingredient.id()));
            }
            quantities.put(ingredient, item.quantity());
        }
        return quantities;
    }

    public record IngredientQuantity(String ingredient, Integer quantity) {
    }
}
