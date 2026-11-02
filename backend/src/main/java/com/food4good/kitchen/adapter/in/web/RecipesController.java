package com.food4good.kitchen.adapter.in.web;

import com.food4good.kitchen.application.ListRecipes;
import com.food4good.kitchen.domain.Recipe;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
class RecipesController {

    private final ListRecipes listRecipes;

    RecipesController(ListRecipes listRecipes) {
        this.listRecipes = listRecipes;
    }

    @GetMapping
    List<RecipeResponse> recipes() {
        return listRecipes.list().stream().map(RecipeResponse::from).toList();
    }

    record RecipeResponse(String name, long preparationTimeSeconds, List<IngredientQuantity> ingredients) {

        static RecipeResponse from(Recipe recipe) {
            return new RecipeResponse(
                    recipe.name(),
                    recipe.preparationTime().toSeconds(),
                    recipe.ingredients().entrySet().stream()
                            .map(entry -> new IngredientQuantity(entry.getKey().id(), entry.getValue()))
                            .toList());
        }
    }

    record IngredientQuantity(String ingredient, int quantity) {
    }
}
