package com.food4good.storage.domain.model;

import java.util.UUID;

/** A queued change to Food Storage, applied by the single stock writer in the order it was queued. */
public sealed interface StorageCommand {

    long id();

    record Reserve(long id, UUID orderId) implements StorageCommand {
    }

    record Consume(long id, UUID orderId) implements StorageCommand {
    }
}
