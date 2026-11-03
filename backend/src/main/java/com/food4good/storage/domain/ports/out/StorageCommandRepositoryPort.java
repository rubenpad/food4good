package com.food4good.storage.domain.ports.out;

import com.food4good.storage.domain.model.MarketJob;
import com.food4good.storage.domain.model.StorageCommand;
import java.util.List;
import java.util.UUID;

public interface StorageCommandRepositoryPort {

    /** Queues reservations and wakes the stock writer once the transaction commits. */
    void enqueueReservations(List<UUID> orderIds);

    /** Queues consuming these Orders' Reservations and wakes the stock writer once the transaction commits. */
    void enqueueConsumptions(List<UUID> orderIds);

    /** Queues adding what a market job bought and wakes the stock writer once the transaction commits. */
    void enqueuePurchase(MarketJob job, int quantity);

    /** The oldest pending commands, in the order they were queued. */
    List<StorageCommand> findPending(int limit);

    void markDone(long commandId);

    /** Counts a failed attempt, and marks the command failed once it reaches {@code maxAttempts}. */
    void recordFailure(long commandId, String error, int maxAttempts);
}
