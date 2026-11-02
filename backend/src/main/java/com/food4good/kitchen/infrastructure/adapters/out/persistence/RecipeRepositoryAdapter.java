package com.food4good.kitchen.infrastructure.adapters.out.persistence;

import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import com.food4good.shared.domain.model.Ingredient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RecipeRepositoryAdapter implements RecipeRepositoryPort {

    private static final String FIND_ALL = """
            SELECT r.id, r.name, r.preparation_time_seconds, i.ingredient, i.quantity
            FROM recipe r
            JOIN recipe_ingredient i ON i.recipe_id = r.id
            ORDER BY r.name, i.ingredient
            """;

    private final JdbcClient jdbc;

    RecipeRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Recipe> findAll() {
        var rows = jdbc.sql(FIND_ALL)
                .query((resultSet, rowNumber) -> new RecipeIngredientRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getInt("preparation_time_seconds"),
                        Ingredient.fromId(resultSet.getString("ingredient")),
                        resultSet.getInt("quantity")))
                .list();

        var recipes = new LinkedHashMap<UUID, RecipeIngredients>();
        rows.forEach(row -> recipes
                .computeIfAbsent(row.id(), id -> new RecipeIngredients(row, new LinkedHashMap<>()))
                .ingredients()
                .put(row.ingredient(), row.quantity()));
        return recipes.values().stream().map(RecipeIngredients::toRecipe).toList();
    }

    private record RecipeIngredientRow(
            UUID id, String name, int preparationTimeSeconds, Ingredient ingredient, int quantity) {
    }

    private record RecipeIngredients(RecipeIngredientRow recipe, Map<Ingredient, Integer> ingredients) {

        Recipe toRecipe() {
            return new Recipe(
                    recipe.id(), recipe.name(), Duration.ofSeconds(recipe.preparationTimeSeconds()), ingredients);
        }
    }
}
