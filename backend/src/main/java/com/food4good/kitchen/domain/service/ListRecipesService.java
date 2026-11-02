package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.ports.in.ListRecipesUseCase;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import java.util.List;

public class ListRecipesService implements ListRecipesUseCase {

    private final RecipeRepositoryPort recipes;

    public ListRecipesService(RecipeRepositoryPort recipes) {
        this.recipes = recipes;
    }

    @Override
    public List<Recipe> list() {
        return recipes.findAll();
    }
}
