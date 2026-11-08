package com.food4good.kitchen.domain.model;

import java.util.UUID;

/** An Order and the trace context of its acceptance, which the work done on it later links back to. */
public record TracedOrder(UUID id, String cause) {
}
