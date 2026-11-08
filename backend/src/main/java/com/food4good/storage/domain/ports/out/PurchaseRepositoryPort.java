package com.food4good.storage.domain.ports.out;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.model.Purchase;
import java.util.List;

public interface PurchaseRepositoryPort {

    /** Returns the new Purchase's id. */
    long insert(long marketJobId, int attempt, Ingredient ingredient, int quantity);

    /** The Purchases with these ids, oldest first. */
    List<Purchase> findByIds(List<Long> ids);

    /** Up to {@code limit} Purchases, newest first, older than {@code before} if it isn't null. */
    List<Purchase> find(Long before, int limit);
}
