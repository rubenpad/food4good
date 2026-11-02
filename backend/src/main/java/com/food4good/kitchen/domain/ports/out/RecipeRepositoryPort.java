package com.food4good.kitchen.domain.ports.out;

import com.food4good.kitchen.domain.model.DuplicateRecipeNameException;
import com.food4good.kitchen.domain.model.Recipe;
import java.util.List;

/** Where the Kitchen's Recipes are kept. */
public interface RecipeRepositoryPort {

    /** Every Recipe, ordered by name, each with its Ingredients ordered by name. */
    List<Recipe> findAll();

    /** @throws DuplicateRecipeNameException if another Recipe has this name */
    void insert(Recipe recipe);

    /**
     * Replaces the stored Recipe with the same id.
     *
     * @return false if there is no such Recipe
     * @throws DuplicateRecipeNameException if another Recipe has this name
     */
    boolean update(Recipe recipe);
}
