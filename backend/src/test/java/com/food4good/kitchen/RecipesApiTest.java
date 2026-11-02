package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipesApiTest extends ApiTest {

    @Test
    void listsTheStartingRecipesByName() {
        List<String> names = getJson("/recipes").read("$[*].name");

        assertThat(names).containsExactly(
                "Cheeseburger",
                "Fries with Ketchup",
                "Grilled Chicken Salad",
                "Lemon Chicken & Rice",
                "Loaded Potatoes",
                "Tomato Rice");
    }

    @Test
    void describesEachRecipeWithIngredientsQuantitiesAndPreparationTime() {
        Map<String, Object> salad = getJson("/recipes").read("$[2]");

        assertThat(salad.get("id")).isNotNull();
        assertThat(salad).containsEntry("name", "Grilled Chicken Salad");
        assertThat(salad).containsEntry("preparationTimeSeconds", 90);
        assertThat(salad.get("ingredients")).isEqualTo(List.of(
                Map.of("ingredient", "chicken", "quantity", 1),
                Map.of("ingredient", "lemon", "quantity", 1),
                Map.of("ingredient", "lettuce", "quantity", 2),
                Map.of("ingredient", "tomato", "quantity", 1)));
    }

    @Test
    void startingRecipesUseEveryIngredient() {
        List<String> used = getJson("/recipes").read("$[*].ingredients[*].ingredient");

        assertThat(used).containsAll(List.of(
                "tomato", "lemon", "potato", "rice", "ketchup",
                "lettuce", "onion", "cheese", "meat", "chicken"));
    }
}
