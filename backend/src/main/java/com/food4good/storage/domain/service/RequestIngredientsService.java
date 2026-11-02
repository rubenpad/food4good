package com.food4good.storage.domain.service;

import com.food4good.storage.domain.ports.in.IngredientRequest;
import com.food4good.storage.domain.ports.in.RequestIngredientsUseCase;
import com.food4good.storage.domain.ports.out.ReservationRepositoryPort;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import java.util.List;

public class RequestIngredientsService implements RequestIngredientsUseCase {

    private final ReservationRepositoryPort reservations;
    private final StorageCommandRepositoryPort commands;

    public RequestIngredientsService(ReservationRepositoryPort reservations, StorageCommandRepositoryPort commands) {
        this.reservations = reservations;
        this.commands = commands;
    }

    @Override
    public void request(List<IngredientRequest> requests) {
        reservations.insertLines(requests);
        commands.enqueueReservations(requests.stream().map(IngredientRequest::orderId).toList());
    }
}
