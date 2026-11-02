package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.model.RecipeDetails;
import com.food4good.kitchen.domain.ports.in.CreateRecipeUseCase;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import java.util.UUID;

public class CreateRecipeService implements CreateRecipeUseCase {

    private final RecipeRepositoryPort recipes;

    public CreateRecipeService(RecipeRepositoryPort recipes) {
        this.recipes = recipes;
    }

    @Override
    public Recipe create(RecipeDetails details) {
        var recipe = Recipe.of(UUID.randomUUID(), details);
        recipes.insert(recipe);
        return recipe;
    }
}
