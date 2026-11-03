package com.food4good.storage.domain.ports.in;

import com.food4good.storage.domain.model.PurchasePage;

/** Reads the Purchase history, newest first. */
public interface ListPurchasesUseCase {

    /** @param before only Purchases older than this id; null starts from the newest */
    PurchasePage list(Long before, int limit);
}
