package com.food4good.shared.domain.ports.out;

import java.util.function.Supplier;

/** Runs domain work atomically, including calls into other modules, which share one database. */
public interface TransactionPort {

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
