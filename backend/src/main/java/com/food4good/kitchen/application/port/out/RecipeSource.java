package com.food4good.kitchen.application.port.out;

import com.food4good.kitchen.domain.Recipe;
import java.util.List;

/** Where the Kitchen's fixed set of Recipes comes from. */
public interface RecipeSource {

    List<Recipe> recipes();
}
