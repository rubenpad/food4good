package com.food4good.kitchen.domain.ports.in;

import com.food4good.kitchen.domain.model.PlacedOrders;

/** Accepts Orders, drawing a Recipe at random for each and requesting its Ingredients. */
public interface PlaceOrdersUseCase {

    PlacedOrders place(int quantity);
}
