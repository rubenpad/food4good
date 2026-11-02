package com.food4good.kitchen.domain.ports.out;

import com.food4good.kitchen.domain.model.Recipe;
import java.util.List;

/** Where the Kitchen's Recipes are kept. */
public interface RecipeRepositoryPort {

    /** Every Recipe, ordered by name, each with its Ingredients ordered by name. */
    List<Recipe> findAll();
}
