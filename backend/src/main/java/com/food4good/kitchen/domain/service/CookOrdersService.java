package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.KitchenSettings;
import com.food4good.kitchen.domain.model.TracedOrder;
import com.food4good.kitchen.domain.ports.in.CookOrdersUseCase;
import com.food4good.kitchen.domain.ports.out.OrderChangesPort;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import com.food4good.shared.domain.ports.out.TracingPort;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.ConsumeReservationUseCase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

public class CookOrdersService implements CookOrdersUseCase {

    private final OrderRepositoryPort orders;
    private final ConsumeReservationUseCase consumeReservation;
    private final TransactionPort transactions;
    private final OrderChangesPort orderChanges;
    private final KitchenSettings settings;
    private final TracingPort tracing;

    public CookOrdersService(
            OrderRepositoryPort orders,
            ConsumeReservationUseCase consumeReservation,
            TransactionPort transactions,
            OrderChangesPort orderChanges,
            KitchenSettings settings,
            TracingPort tracing) {
        this.orders = orders;
        this.consumeReservation = consumeReservation;
        this.transactions = transactions;
        this.orderChanges = orderChanges;
        this.settings = settings;
        this.tracing = tracing;
    }

    @Override
    public Optional<Duration> cook() {
        transactions.inTransaction(() -> {
            var delivered = orders.deliverDue();
            delivered.forEach(order -> tracing.record("Deliver", order.cause(), Map.of("order.id", order.id())));
            orderChanges.changed(ids(delivered));
            var freeCooks = settings.cooks() - orders.countPreparing();
            if (freeCooks > 0) {
                var started = orders.startPreparing(freeCooks);
                // One at a time, so each Order's consumption is queued from its own span.
                started.forEach(order -> tracing.run("StartCooking", order.cause(), Map.of("order.id", order.id()),
                        () -> consumeReservation.consume(List.of(order.id()))));
                orderChanges.changed(ids(started));
            }
        });
        return orders.timeUntilNextDishIsDue();
    }

    private static List<UUID> ids(List<TracedOrder> orders) {
        return orders.stream().map(TracedOrder::id).toList();
    }
}
