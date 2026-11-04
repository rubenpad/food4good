package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.InvalidOrderRequestException;
import com.food4good.kitchen.domain.model.PlacedOrders;
import com.food4good.kitchen.domain.ports.in.PlaceOrdersUseCase;
import com.food4good.kitchen.domain.ports.out.AdmissionQueuePort;

public class PlaceOrdersService implements PlaceOrdersUseCase {

    /** Protects the server, not a business limit. */
    private static final int MAX_QUANTITY = 10_000;

    private final AdmissionQueuePort admissionQueue;

    public PlaceOrdersService(AdmissionQueuePort admissionQueue) {
        this.admissionQueue = admissionQueue;
    }

    @Override
    public PlacedOrders place(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidOrderRequestException("Quantity must be between 1 and %d".formatted(MAX_QUANTITY));
        }
        return admissionQueue.admit(quantity);
    }
}
