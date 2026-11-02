package com.food4good.kitchen.domain.ports.in;

import com.food4good.kitchen.domain.model.OrderPage;
import com.food4good.kitchen.domain.model.OrderQuery;

/** Reads the Order history, newest first. */
public interface ListOrdersUseCase {

    OrderPage list(OrderQuery query);
}
