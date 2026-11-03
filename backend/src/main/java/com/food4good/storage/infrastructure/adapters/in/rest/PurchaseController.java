package com.food4good.storage.infrastructure.adapters.in.rest;

import com.food4good.storage.domain.model.InvalidPurchaseQueryException;
import com.food4good.storage.domain.ports.in.ListPurchasesUseCase;
import com.food4good.storage.infrastructure.adapters.in.rest.dto.PurchasePageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
class PurchaseController {

    private final ListPurchasesUseCase listPurchases;

    PurchaseController(ListPurchasesUseCase listPurchases) {
        this.listPurchases = listPurchases;
    }

    @GetMapping
    PurchasePageResponse history(
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "50") int limit) {
        return PurchasePageResponse.from(listPurchases.list(parseCursor(cursor), limit));
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException e) {
            throw new InvalidPurchaseQueryException("That cursor is not valid");
        }
    }
}
