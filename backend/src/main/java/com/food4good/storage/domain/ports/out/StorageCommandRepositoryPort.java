package com.food4good.storage.domain.ports.out;

import com.food4good.storage.domain.model.MarketJob;
import com.food4good.storage.domain.model.StorageCommand;
import com.food4good.storage.domain.ports.in.IngredientRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageCommandRepositoryPort {

    /** Queues these Ingredient Requests as one command and wakes the stock writer once the transaction commits. */
    void enqueueRequests(List<IngredientRequest> requests);

    /** Queues reservations and wakes the stock writer once the transaction commits. */
    void enqueueReservations(List<UUID> orderIds);

    /** Queues consuming these Orders' Reservations and wakes the stock writer once the transaction commits. */
    void enqueueConsumptions(List<UUID> orderIds);

    /** Queues adding what a market job bought and wakes the stock writer once the transaction commits. */
    void enqueuePurchase(MarketJob job, int quantity);

    /** The oldest pending commands, in the order they were queued. */
    List<StorageCommand> findPending(int limit);

    void markDone(long commandId);

    /**
     * Counts a failed attempt, and marks the command failed once it reaches {@code maxAttempts}.
     *
     * @return true if this attempt was the last
     */
    boolean recordFailure(long commandId, String error, int maxAttempts);

    /** Queues a repair: the Order's request again if it has no Reservation lines, else its reservation again. */
    void enqueueRepair(UUID orderId, IngredientRequest requestOrNull, int repair);

    /** The Ingredient Request recorded for this Order when it was accepted, whatever became of that command. */
    Optional<IngredientRequest> findRequest(UUID orderId);
}
