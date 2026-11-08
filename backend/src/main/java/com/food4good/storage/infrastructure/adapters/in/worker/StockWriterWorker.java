package com.food4good.storage.infrastructure.adapters.in.worker;

import com.food4good.shared.infrastructure.adapters.in.worker.SingleInstanceWorker;
import com.food4good.storage.domain.ports.in.ProcessStorageCommandsUseCase;
import com.food4good.storage.domain.ports.in.ReconcileStuckOrdersUseCase;
import com.food4good.storage.infrastructure.configuration.StorageProperties;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The single stock writer (ADR-0001). Between commands, it also runs the reconciler now and then. */
@Component
class StockWriterWorker extends SingleInstanceWorker {

    private static final Logger log = LoggerFactory.getLogger(StockWriterWorker.class);
    private static final long LOCK_KEY = 4_004_001L;

    private final ProcessStorageCommandsUseCase processCommands;
    private final ReconcileStuckOrdersUseCase reconcile;
    private final long reconcileEveryNanos;
    private long lastReconciled = System.nanoTime();

    StockWriterWorker(
            DataSource dataSource,
            ProcessStorageCommandsUseCase processCommands,
            ReconcileStuckOrdersUseCase reconcile,
            StorageProperties properties) {
        super(dataSource, "stock writer", LOCK_KEY, "storage_commands");
        this.processCommands = processCommands;
        this.reconcile = reconcile;
        this.reconcileEveryNanos = properties.reconcileInterval().toNanos();
    }

    @Override
    protected Duration runOnce() {
        if (processCommands.processPending() > 0) {
            return Duration.ZERO;
        }
        if (System.nanoTime() - lastReconciled >= reconcileEveryNanos) {
            lastReconciled = System.nanoTime();
            try {
                reconcile.reconcile();
            } catch (RuntimeException e) {
                log.warn("Reconciling stuck Orders failed; trying again next time", e);
            }
            return Duration.ZERO;
        }
        return Duration.ofSeconds(1);
    }
}
