package com.food4good.storage.infrastructure.adapters.in.worker;

import com.food4good.storage.domain.ports.in.ProcessStorageCommandsUseCase;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * The single stock writer (ADR-0001). A session advisory lock on one dedicated connection makes it
 * the only one across all instances; other instances stand by and take over when the lock frees.
 * It wakes on NOTIFY, with a poll as backup in case a notification is missed.
 */
@Component
class StockWriterWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StockWriterWorker.class);
    private static final long WRITER_LOCK = 4_004_001L;
    private static final int POLL_MILLIS = 1_000;

    private final DataSource dataSource;
    private final ProcessStorageCommandsUseCase processCommands;

    private volatile boolean running;
    private Thread thread;

    StockWriterWorker(DataSource dataSource, ProcessStorageCommandsUseCase processCommands) {
        this.dataSource = dataSource;
        this.processCommands = processCommands;
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofVirtual().name("stock-writer").start(this::run);
    }

    @Override
    public void stop() {
        running = false;
        try {
            thread.join(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void run() {
        while (running) {
            try (var connection = dataSource.getConnection()) {
                if (tryLock(connection)) {
                    writeUntilStopped(connection);
                }
            } catch (SQLException | RuntimeException e) {
                log.warn("Stock writer lost its connection or failed; retrying", e);
            }
            pause();
        }
    }

    private void writeUntilStopped(Connection connection) throws SQLException {
        log.info("This instance is now the stock writer");
        try (var statement = connection.createStatement()) {
            statement.execute("LISTEN storage_commands");
        }
        try {
            var notifications = connection.unwrap(PGConnection.class);
            while (running) {
                var drained = processCommands.processPending() == 0;
                if (drained) {
                    notifications.getNotifications(POLL_MILLIS);
                }
            }
        } finally {
            // The connection returns to the pool, so its session must not keep the lock or the listener.
            try (var statement = connection.createStatement()) {
                statement.execute("UNLISTEN *");
                statement.execute("SELECT pg_advisory_unlock(" + WRITER_LOCK + ")");
            }
        }
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT pg_try_advisory_lock(" + WRITER_LOCK + ")")) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private void pause() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
