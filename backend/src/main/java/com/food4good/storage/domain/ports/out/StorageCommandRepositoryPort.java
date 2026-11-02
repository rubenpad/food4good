package com.food4good.storage.domain.ports.out;

import com.food4good.storage.domain.model.StorageCommand;
import java.util.List;
import java.util.UUID;

public interface StorageCommandRepositoryPort {

    /** Queues reservations and wakes the stock writer once the transaction commits. */
    void enqueueReservations(List<UUID> orderIds);

    /** The oldest pending commands, in the order they were queued. */
    List<StorageCommand> findPending(int limit);

    void markDone(long commandId);

    /** Counts a failed attempt, and marks the command failed once it reaches {@code maxAttempts}. */
    void recordFailure(long commandId, String error, int maxAttempts);
}
