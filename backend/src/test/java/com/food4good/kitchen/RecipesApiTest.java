package com.food4good.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.food4good.ApiTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

class RecipesApiTest {

    @Nested
    class WithDefaultConfiguration extends ApiTest {

        @Test
        void listsTheSixConfiguredRecipes() {
            List<String> names = getJson("/recipes").read("$[*].name");

            assertThat(names).containsExactly(
                    "Grilled Chicken Salad",
                    "Cheeseburger",
                    "Lemon Chicken & Rice",
                    "Loaded Potatoes",
                    "Tomato Rice",
                    "Fries with Ketchup");
        }

        @Test
        void describesEachRecipeWithIngredientsQuantitiesAndPreparationTime() {
            Map<String, Object> salad = getJson("/recipes").read("$[0]");

            assertThat(salad).containsEntry("preparationTimeSeconds", 90);
            assertThat(salad.get("ingredients")).isEqualTo(List.of(
                    Map.of("ingredient", "chicken", "quantity", 1),
                    Map.of("ingredient", "lettuce", "quantity", 2),
                    Map.of("ingredient", "tomato", "quantity", 1),
                    Map.of("ingredient", "lemon", "quantity", 1)));
        }

        @Test
        void usesEveryIngredientInAtLeastOneRecipe() {
            List<String> used = getJson("/recipes").read("$[*].ingredients[*].ingredient");

            assertThat(used).containsAll(List.of(
                    "tomato", "lemon", "potato", "rice", "ketchup",
                    "lettuce", "onion", "cheese", "meat", "chicken"));
        }
    }

    @Nested
    @TestPropertySource(properties = {
            "food4good.kitchen.recipes[0].name=Plain Rice",
            "food4good.kitchen.recipes[0].preparation-time=45s",
            "food4good.kitchen.recipes[0].ingredients.rice=3",
    })
    class WithRecipesFromConfiguration extends ApiTest {

        @Test
        void servesWhateverRecipesAreConfigured() {
            List<Map<String, Object>> recipes = getJson("/recipes").read("$");

            assertThat(recipes).containsExactly(Map.of(
                    "name", "Plain Rice",
                    "preparationTimeSeconds", 45,
                    "ingredients", List.of(Map.of("ingredient", "rice", "quantity", 3))));
        }
    }
}
