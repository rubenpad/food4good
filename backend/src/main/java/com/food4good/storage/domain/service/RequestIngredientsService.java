package com.food4good.storage.domain.service;

import com.food4good.storage.domain.ports.in.IngredientRequest;
import com.food4good.storage.domain.ports.in.RequestIngredientsUseCase;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import java.util.List;

public class RequestIngredientsService implements RequestIngredientsUseCase {

    private final StorageCommandRepositoryPort commands;

    public RequestIngredientsService(StorageCommandRepositoryPort commands) {
        this.commands = commands;
    }

    @Override
    public void request(List<IngredientRequest> requests) {
        commands.enqueueRequests(requests);
    }
}
