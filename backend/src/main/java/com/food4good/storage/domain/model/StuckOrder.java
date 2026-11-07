package com.food4good.storage.domain.model;

import java.util.UUID;

/** An Order waiting for ingredients that nothing is working on, and how often it was repaired already. */
public record StuckOrder(UUID orderId, boolean hasReservationLines, int repairs) {
}
