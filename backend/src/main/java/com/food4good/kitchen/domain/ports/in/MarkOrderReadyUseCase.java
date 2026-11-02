package com.food4good.kitchen.domain.ports.in;

import java.util.UUID;

/** Food Storage calls this when an Order's Reservation is complete. Marking it twice has no further effect. */
public interface MarkOrderReadyUseCase {

    void markReady(UUID orderId);
}
