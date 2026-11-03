package com.food4good.storage.domain.ports.in;

import java.util.List;
import java.util.UUID;

/**
 * Queues taking these Orders' reserved units out of Food Storage, for when a Cook starts preparing.
 * Runs in the caller's transaction.
 */
public interface ConsumeReservationUseCase {

    void consume(List<UUID> orderIds);
}
