package com.food4good.kitchen.infrastructure.adapters.in.rest.dto;

import com.food4good.kitchen.domain.model.PlacedOrders;
import java.util.List;
import java.util.UUID;

public record PlaceOrdersResponse(int requested, int accepted, List<UUID> orderIds, long estimatedWaitSeconds) {

    public static PlaceOrdersResponse from(PlacedOrders placed) {
        return new PlaceOrdersResponse(
                placed.requested(),
                placed.accepted(),
                placed.orderIds(),
                placed.estimatedWait().plusMillis(999).toSeconds());
    }
}
