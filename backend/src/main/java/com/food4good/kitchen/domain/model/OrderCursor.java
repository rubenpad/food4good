package com.food4good.kitchen.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Marks the last Order of a history page; the next page starts just after it. */
public record OrderCursor(Instant acceptedAt, UUID id) {
}
