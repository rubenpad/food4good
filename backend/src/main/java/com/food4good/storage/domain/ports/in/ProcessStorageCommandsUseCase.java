package com.food4good.storage.domain.ports.in;

/** Applies queued stock changes. Only the single stock writer may call this (ADR-0001). */
public interface ProcessStorageCommandsUseCase {

    /** @return how many commands it looked at; 0 means the queue is empty */
    int processPending();
}
