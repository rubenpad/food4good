package com.food4good.kitchen.domain.ports.in;

import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.model.RecipeDetails;

/** Adds a Recipe, which joins the random draw for the next Order. */
public interface CreateRecipeUseCase {

    Recipe create(RecipeDetails details);
}
