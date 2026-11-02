package com.food4good.storage.domain.ports.out;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.model.IngredientStock;
import java.util.List;

public interface StockRepositoryPort {

    List<IngredientStock> findAll();

    IngredientStock find(Ingredient ingredient);

    void save(IngredientStock stock);
}
