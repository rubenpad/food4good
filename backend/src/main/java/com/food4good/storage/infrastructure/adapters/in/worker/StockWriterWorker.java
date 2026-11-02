package com.food4good.storage.infrastructure.adapters.in.worker;

import com.food4good.shared.infrastructure.adapters.in.worker.SingleInstanceWorker;
import com.food4good.storage.domain.ports.in.ProcessStorageCommandsUseCase;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** The single stock writer (ADR-0001). */
@Component
class StockWriterWorker extends SingleInstanceWorker {

    private static final long LOCK_KEY = 4_004_001L;

    private final ProcessStorageCommandsUseCase processCommands;

    StockWriterWorker(DataSource dataSource, ProcessStorageCommandsUseCase processCommands) {
        super(dataSource, "stock writer", LOCK_KEY, "storage_commands");
        this.processCommands = processCommands;
    }

    @Override
    protected Duration runOnce() {
        return processCommands.processPending() == 0 ? Duration.ofSeconds(1) : Duration.ZERO;
    }
}
