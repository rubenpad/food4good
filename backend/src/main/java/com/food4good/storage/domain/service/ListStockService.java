package com.food4good.storage.domain.service;

import com.food4good.storage.domain.model.IngredientStock;
import com.food4good.storage.domain.ports.in.ListStockUseCase;
import com.food4good.storage.domain.ports.out.StockRepositoryPort;
import java.util.List;

public class ListStockService implements ListStockUseCase {

    private final StockRepositoryPort stock;

    public ListStockService(StockRepositoryPort stock) {
        this.stock = stock;
    }

    @Override
    public List<IngredientStock> list() {
        return stock.findAll();
    }
}
