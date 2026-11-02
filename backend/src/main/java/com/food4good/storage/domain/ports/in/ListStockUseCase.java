package com.food4good.storage.domain.ports.in;

import com.food4good.storage.domain.model.IngredientStock;
import java.util.List;

/** Reads what Food Storage holds, ordered by Ingredient name. */
public interface ListStockUseCase {

    List<IngredientStock> list();
}
