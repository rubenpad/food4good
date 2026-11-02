package com.food4good.kitchen.domain.ports.in;

import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.model.RecipeDetails;
import java.util.UUID;

/** Replaces a Recipe. Orders already accepted keep what they were accepted with. */
public interface UpdateRecipeUseCase {

    Recipe update(UUID id, RecipeDetails details);
}
