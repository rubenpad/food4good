package com.food4good.kitchen.adapter.out.config;

import com.food4good.storage.Ingredient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kitchen settings, bound from {@code food4good.kitchen.*} (and so from {@code FOOD4GOOD_KITCHEN_*} env vars). */
@ConfigurationProperties("food4good.kitchen")
record KitchenProperties(List<RecipeProperties> recipes) {

    KitchenProperties {
        recipes = recipes == null ? List.of() : recipes;
    }

    record RecipeProperties(String name, Duration preparationTime, Map<Ingredient, Integer> ingredients) {
    }
}
