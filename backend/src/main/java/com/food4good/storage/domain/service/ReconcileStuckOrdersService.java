package com.food4good.storage.domain.service;

import com.food4good.shared.domain.ports.out.TracingPort;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.ReconcileStuckOrdersUseCase;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import com.food4good.storage.domain.ports.out.StuckOrderRepositoryPort;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A repair queues the Order's reservation again, which reserves what is available and brings its
 * market jobs back. With no Reservation lines at all, its Ingredient Request is queued again from
 * the command it was accepted with. After a failed ADD_PURCHASE, this buys those units again and
 * the original sale stays lost.
 */
public class ReconcileStuckOrdersService implements ReconcileStuckOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconcileStuckOrdersService.class);
    /** So a repeating defect can't keep buying forever. */
    private static final int MAX_REPAIRS = 3;
    private static final int BATCH_SIZE = 100;

    private final StuckOrderRepositoryPort stuckOrders;
    private final StorageCommandRepositoryPort commands;
    private final TransactionPort transactions;
    private final TracingPort tracing;
    private final Set<UUID> givenUp = ConcurrentHashMap.newKeySet();

    public ReconcileStuckOrdersService(
            StuckOrderRepositoryPort stuckOrders,
            StorageCommandRepositoryPort commands,
            TransactionPort transactions,
            TracingPort tracing) {
        this.stuckOrders = stuckOrders;
        this.commands = commands;
        this.transactions = transactions;
        this.tracing = tracing;
    }

    @Override
    public void reconcile() {
        transactions.inTransaction(() -> stuckOrders.findStuck(MAX_REPAIRS, BATCH_SIZE).forEach(stuck -> {
            var orderId = stuck.orderId();
            if (stuck.repairs() >= MAX_REPAIRS) {
                if (givenUp.add(orderId)) {
                    log.error("Giving up on Order {} after {} repairs; it stays waiting for ingredients", orderId, MAX_REPAIRS);
                }
                return;
            }
            var request = stuck.hasReservationLines() ? null : commands.findRequest(orderId).orElse(null);
            if (!stuck.hasReservationLines() && request == null) {
                if (givenUp.add(orderId)) {
                    log.error("Can't repair Order {}: its Ingredient Request is gone", orderId);
                }
                return;
            }
            var repair = stuck.repairs() + 1;
            // Its own trace: nothing links a repair to the work that went wrong, only the Order's id.
            tracing.run("RepairOrder", null, Map.of("order.id", orderId, "repair", repair), () -> {
                log.warn("Repairing Order {} (repair {} of {})", orderId, repair, MAX_REPAIRS);
                commands.enqueueRepair(orderId, request, repair);
            });
        }));
    }
}
