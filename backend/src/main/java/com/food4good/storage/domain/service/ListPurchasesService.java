package com.food4good.storage.domain.service;

import com.food4good.storage.domain.model.InvalidPurchaseQueryException;
import com.food4good.storage.domain.model.PurchasePage;
import com.food4good.storage.domain.ports.in.ListPurchasesUseCase;
import com.food4good.storage.domain.ports.out.PurchaseRepositoryPort;

public class ListPurchasesService implements ListPurchasesUseCase {

    private static final int MAX_LIMIT = 200;

    private final PurchaseRepositoryPort purchases;

    public ListPurchasesService(PurchaseRepositoryPort purchases) {
        this.purchases = purchases;
    }

    @Override
    public PurchasePage list(Long before, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidPurchaseQueryException("limit must be between 1 and %d".formatted(MAX_LIMIT));
        }
        var found = purchases.find(before, limit + 1);
        if (found.size() <= limit) {
            return new PurchasePage(found, null);
        }
        var page = found.subList(0, limit);
        return new PurchasePage(page, page.getLast().id());
    }
}
