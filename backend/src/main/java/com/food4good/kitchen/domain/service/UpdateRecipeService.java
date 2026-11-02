package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.model.RecipeDetails;
import com.food4good.kitchen.domain.model.RecipeNotFoundException;
import com.food4good.kitchen.domain.ports.in.UpdateRecipeUseCase;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import java.util.UUID;

public class UpdateRecipeService implements UpdateRecipeUseCase {

    private final RecipeRepositoryPort recipes;

    public UpdateRecipeService(RecipeRepositoryPort recipes) {
        this.recipes = recipes;
    }

    @Override
    public Recipe update(UUID id, RecipeDetails details) {
        var recipe = Recipe.of(id, details);
        if (!recipes.update(recipe)) {
            throw new RecipeNotFoundException(id);
        }
        return recipe;
    }
}
