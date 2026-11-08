package com.food4good.storage.domain.ports.out;

/** Announces a new Purchase. Within a transaction, it only takes effect if that transaction commits. */
public interface PurchaseChangesPort {

    void added(long purchaseId);
}
