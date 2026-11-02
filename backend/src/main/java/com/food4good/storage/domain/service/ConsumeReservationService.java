package com.food4good.storage.domain.service;

import com.food4good.storage.domain.ports.in.ConsumeReservationUseCase;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import java.util.List;
import java.util.UUID;

public class ConsumeReservationService implements ConsumeReservationUseCase {

    private final StorageCommandRepositoryPort commands;

    public ConsumeReservationService(StorageCommandRepositoryPort commands) {
        this.commands = commands;
    }

    @Override
    public void consume(List<UUID> orderIds) {
        commands.enqueueConsumptions(orderIds);
    }
}
