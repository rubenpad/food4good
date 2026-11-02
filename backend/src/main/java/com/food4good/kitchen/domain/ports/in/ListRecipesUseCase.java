package com.food4good.kitchen.domain.ports.in;

import com.food4good.kitchen.domain.model.Recipe;
import java.util.List;

/** Lists the Recipes the Kitchen can prepare, ordered by name. */
public interface ListRecipesUseCase {

    List<Recipe> list();
}
