package com.food4good.storage.domain.ports.out;

/** Announces that stock changed. Within a transaction, it only takes effect if that transaction commits. */
public interface StockChangesPort {

    void changed();
}
