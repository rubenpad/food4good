package com.food4good.kitchen.infrastructure.adapters.out.persistence;

import com.food4good.kitchen.domain.model.DuplicateRecipeNameException;
import com.food4good.kitchen.domain.model.Recipe;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import com.food4good.shared.domain.model.Ingredient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public void insert(Recipe recipe) {
        try {
            jdbc.sql("INSERT INTO recipe (id, name, preparation_time_seconds) VALUES (:id, :name, :seconds)")
                    .param("id", recipe.id())
                    .param("name", recipe.name())
                    .param("seconds", seconds(recipe))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateRecipeNameException(recipe.name());
        }
        insertIngredients(recipe);
    }

    @Override
    @Transactional
    public boolean update(Recipe recipe) {
        int updated;
        try {
            updated = jdbc.sql("UPDATE recipe SET name = :name, preparation_time_seconds = :seconds WHERE id = :id")
                    .param("id", recipe.id())
                    .param("name", recipe.name())
                    .param("seconds", seconds(recipe))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateRecipeNameException(recipe.name());
        }
        if (updated == 0) {
            return false;
        }
        jdbc.sql("DELETE FROM recipe_ingredient WHERE recipe_id = :id").param("id", recipe.id()).update();
        insertIngredients(recipe);
        return true;
    }

    private void insertIngredients(Recipe recipe) {
        recipe.ingredients().forEach((ingredient, quantity) -> jdbc
                .sql("INSERT INTO recipe_ingredient (recipe_id, ingredient, quantity) VALUES (:id, :ingredient, :quantity)")
                .param("id", recipe.id())
                .param("ingredient", ingredient.id())
                .param("quantity", quantity)
                .update());
    }

    private static int seconds(Recipe recipe) {
        return Math.toIntExact(recipe.preparationTime().toSeconds());
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
