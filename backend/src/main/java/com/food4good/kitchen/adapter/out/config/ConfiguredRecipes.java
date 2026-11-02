package com.food4good.kitchen.adapter.out.config;

import com.food4good.kitchen.application.port.out.RecipeSource;
import com.food4good.kitchen.domain.Recipe;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Recipes taken from configuration; invalid configuration fails startup. */
@Component
class ConfiguredRecipes implements RecipeSource {

    private final List<Recipe> recipes;

    ConfiguredRecipes(KitchenProperties properties) {
        this.recipes = properties.recipes().stream()
                .map(recipe -> new Recipe(
                        recipe.name(),
                        recipe.preparationTime(),
                        recipe.ingredients() == null ? Map.of() : recipe.ingredients()))
                .toList();
        if (recipes.isEmpty()) {
            throw new IllegalStateException("No Recipes configured under food4good.kitchen.recipes");
        }
        var names = new HashSet<String>();
        recipes.forEach(recipe -> {
            if (!names.add(recipe.name())) {
                throw new IllegalStateException("Recipe '%s' is configured twice".formatted(recipe.name()));
            }
        });
    }

    @Override
    public List<Recipe> recipes() {
        return recipes;
    }
}
