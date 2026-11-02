package com.food4good.storage.infrastructure.adapters.in.rest;

import com.food4good.storage.domain.ports.in.ListStockUseCase;
import com.food4good.storage.infrastructure.adapters.in.rest.dto.StockResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
class StorageController {

    private final ListStockUseCase listStock;

    StorageController(ListStockUseCase listStock) {
        this.listStock = listStock;
    }

    @GetMapping
    List<StockResponse> stock() {
        return listStock.list().stream().map(StockResponse::from).toList();
    }
}
