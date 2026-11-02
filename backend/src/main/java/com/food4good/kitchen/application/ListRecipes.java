package com.food4good.kitchen.application;

import com.food4good.kitchen.application.port.out.RecipeSource;
import com.food4good.kitchen.domain.Recipe;
import java.util.List;
import org.springframework.stereotype.Service;

/** Lists the Recipes the Kitchen can prepare, in their configured order. */
@Service
public class ListRecipes {

    private final RecipeSource recipeSource;

    ListRecipes(RecipeSource recipeSource) {
        this.recipeSource = recipeSource;
    }

    public List<Recipe> list() {
        return recipeSource.recipes();
    }
}
