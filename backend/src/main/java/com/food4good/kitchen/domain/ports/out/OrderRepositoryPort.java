package com.food4good.kitchen.domain.ports.out;

import com.food4good.kitchen.domain.model.NewOrder;
import com.food4good.kitchen.domain.model.Order;
import com.food4good.kitchen.domain.model.OrderQuery;
import java.util.List;
import java.util.UUID;

/** Where Orders are kept. */
public interface OrderRepositoryPort {

    void insertAll(List<NewOrder> orders);

    /** Up to {@code query.limit() + 1} Orders, newest first, so the caller can tell whether more follow. */
    List<Order> find(OrderQuery query);

    /** Moves an Order waiting for ingredients to ready; has no effect in any other status. */
    void markReady(UUID orderId);
}
