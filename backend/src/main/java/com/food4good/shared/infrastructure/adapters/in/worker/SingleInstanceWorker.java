package com.food4good.shared.infrastructure.adapters.in.worker;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * A background worker that runs on exactly one instance. A session advisory lock on one dedicated
 * connection makes it the only one; other instances stand by and take over when the lock frees. It
 * wakes on NOTIFY, with a poll as backup in case a notification is missed.
 */
public abstract class SingleInstanceWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SingleInstanceWorker.class);
    private static final Duration POLL = Duration.ofSeconds(1);

    private final DataSource dataSource;
    private final String role;
    private final long lockKey;
    private final String channel;

    private volatile boolean running;
    private volatile CountDownLatch stopping;
    private Thread thread;

    protected SingleInstanceWorker(DataSource dataSource, String role, long lockKey, String channel) {
        this.dataSource = dataSource;
        this.role = role;
        this.lockKey = lockKey;
        this.channel = channel;
    }

    /** Does one round of work. @return how long it may sleep; zero means there is more to do right away */
    protected abstract Duration runOnce();

    @Override
    public void start() {
        running = true;
        stopping = new CountDownLatch(1);
        thread = Thread.ofVirtual().name(role).start(this::run);
    }

    @Override
    public void stop() {
        running = false;
        stopping.countDown();
        wakeListener();
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

    /** So a worker waiting on a notification notices the stop now rather than at its next poll. */
    private void wakeListener() {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("NOTIFY " + channel);
        } catch (SQLException e) {
            log.debug("Could not wake the {}; it will notice at its next poll", role, e);
        }
    }

    private void run() {
        while (running) {
            try (var connection = dataSource.getConnection()) {
                if (tryLock(connection)) {
                    workUntilStopped(connection);
                }
            } catch (SQLException | RuntimeException e) {
                log.warn("The {} lost its connection or failed; retrying", role, e);
            }
            pause();
        }
    }

    private void workUntilStopped(Connection connection) throws SQLException {
        log.info("This instance is now the {}", role);
        try (var statement = connection.createStatement()) {
            statement.execute("LISTEN " + channel);
        }
        try {
            var notifications = connection.unwrap(PGConnection.class);
            while (running) {
                var wait = runOnce();
                if (wait.isPositive()) {
                    // A timeout of 0 would block forever, so never go below 1 ms.
                    notifications.getNotifications((int) Math.clamp(wait.toMillis(), 1, POLL.toMillis()));
                }
            }
        } finally {
            // The connection returns to the pool, so its session must not keep the lock or the listener.
            try (var statement = connection.createStatement()) {
                statement.execute("UNLISTEN *");
                statement.execute("SELECT pg_advisory_unlock(" + lockKey + ")");
            }
        }
    }

    private boolean tryLock(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT pg_try_advisory_lock(" + lockKey + ")")) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private void pause() {
        try {
            stopping.await(POLL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
