package com.food4good.kitchen.domain.service;

import com.food4good.kitchen.domain.model.KitchenSettings;
import com.food4good.kitchen.domain.ports.in.CookOrdersUseCase;
import com.food4good.kitchen.domain.ports.out.OrderChangesPort;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.ConsumeReservationUseCase;
import java.time.Duration;
import java.util.Optional;

public class CookOrdersService implements CookOrdersUseCase {

    private final OrderRepositoryPort orders;
    private final ConsumeReservationUseCase consumeReservation;
    private final TransactionPort transactions;
    private final OrderChangesPort orderChanges;
    private final KitchenSettings settings;

    public CookOrdersService(
            OrderRepositoryPort orders,
            ConsumeReservationUseCase consumeReservation,
            TransactionPort transactions,
            OrderChangesPort orderChanges,
            KitchenSettings settings) {
        this.orders = orders;
        this.consumeReservation = consumeReservation;
        this.transactions = transactions;
        this.orderChanges = orderChanges;
        this.settings = settings;
    }

    @Override
    public Optional<Duration> cook() {
        transactions.inTransaction(() -> {
            orderChanges.changed(orders.deliverDue());
            var freeCooks = settings.cooks() - orders.countPreparing();
            if (freeCooks > 0) {
                var started = orders.startPreparing(freeCooks);
                if (!started.isEmpty()) {
                    consumeReservation.consume(started);
                    orderChanges.changed(started);
                }
            }
        });
        return orders.timeUntilNextDishIsDue();
    }
}
