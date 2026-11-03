package com.food4good.kitchen.domain.ports.out;

import com.food4good.kitchen.domain.model.NewOrder;
import com.food4good.kitchen.domain.model.Order;
import com.food4good.kitchen.domain.model.OrderQuery;
import com.food4good.kitchen.domain.model.OrderStatus;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Where Orders are kept. */
public interface OrderRepositoryPort {

    void insertAll(List<NewOrder> orders);

    /** Makes requests to place Orders take turns until the current transaction ends. */
    void lockAdmission();

    /**
     * The preparation work of every Order not yet delivered: its whole preparation time, or for an
     * Order being prepared, what remains of it. Time spent waiting for ingredients is not counted.
     */
    Duration backlogWork();

    List<Order> findByIds(Collection<UUID> ids);

    /** Up to {@code query.limit() + 1} Orders, newest first, so the caller can tell whether more follow. */
    List<Order> find(OrderQuery query);

    /** Moves an Order waiting for ingredients to ready; has no effect in any other status. */
    void markReady(UUID orderId);

    /** Moves every Order whose preparation time has passed to delivered, and returns their ids. */
    List<UUID> deliverDue();

    int countPreparing();

    /** Moves up to {@code limit} ready Orders to preparing, earliest ready first, and returns their ids. */
    List<UUID> startPreparing(int limit);

    /** How long until the next Dish is due, if any is cooking. Never negative. */
    Optional<Duration> timeUntilNextDishIsDue();

    /** How many Orders are in each status, for every status except delivered. */
    Map<OrderStatus, Integer> countOpenByStatus();
}
