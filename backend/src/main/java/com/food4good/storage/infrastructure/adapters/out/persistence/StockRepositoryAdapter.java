package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.model.IngredientStock;
import com.food4good.storage.domain.ports.out.StockRepositoryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StockRepositoryAdapter implements StockRepositoryPort {

    private final JdbcClient jdbc;

    StockRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<IngredientStock> findAll() {
        return jdbc.sql("SELECT ingredient, on_hand, reserved FROM ingredient_stock ORDER BY ingredient")
                .query((row, rowNumber) -> toStock(row))
                .list();
    }

    @Override
    public IngredientStock find(Ingredient ingredient) {
        return jdbc.sql("SELECT ingredient, on_hand, reserved FROM ingredient_stock WHERE ingredient = :ingredient")
                .param("ingredient", ingredient.id())
                .query((row, rowNumber) -> toStock(row))
                .single();
    }

    @Override
    public void save(IngredientStock stock) {
        jdbc.sql("UPDATE ingredient_stock SET on_hand = :onHand, reserved = :reserved WHERE ingredient = :ingredient")
                .param("onHand", stock.onHand())
                .param("reserved", stock.reserved())
                .param("ingredient", stock.ingredient().id())
                .update();
    }

    private static IngredientStock toStock(ResultSet row) throws SQLException {
        return new IngredientStock(
                Ingredient.fromId(row.getString("ingredient")), row.getInt("on_hand"), row.getInt("reserved"));
    }
}
