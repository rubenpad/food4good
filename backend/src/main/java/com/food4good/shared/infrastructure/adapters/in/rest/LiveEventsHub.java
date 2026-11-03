package com.food4good.shared.infrastructure.adapters.in.rest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Sends each channel's changes to every client as one batch per type, at most every 250 ms, so a
 * burst can't flood a browser. Reading the state at send time means repeated changes collapse into
 * one update. Each client has its own queue and thread: a slow client is dropped rather than holding
 * up the others, and it catches up when it reconnects.
 *
 * <p>Only clients of this instance hear about changes made on it. Fan-out across instances needs
 * LISTEN/NOTIFY (RFC §7), so it waits until a second instance is needed.
 */
@Component
class LiveEventsHub implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LiveEventsHub.class);
    private static final Duration FLUSH_INTERVAL = Duration.ofMillis(250);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final int CLIENT_QUEUE_SIZE = 64;

    private final List<LiveChannel> channels;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final AtomicLong batchId = new AtomicLong();

    private volatile boolean running;
    private Thread flusher;

    LiveEventsHub(List<LiveChannel> channels) {
        this.channels = channels;
    }

    SseEmitter subscribe() {
        var client = new Client(new SseEmitter(0L));
        client.emitter.onCompletion(() -> remove(client));
        client.emitter.onTimeout(() -> remove(client));
        client.emitter.onError(error -> remove(client));
        clients.add(client);
        client.thread = Thread.ofVirtual().name("sse-client").start(client::sendQueued);
        // Sends the response headers right away, so the browser knows the stream is open.
        client.offer(SseEmitter.event().comment("connected"));
        return client.emitter;
    }

    @Override
    public void start() {
        running = true;
        flusher = Thread.ofVirtual().name("live-events").start(this::run);
    }

    /** Ends every stream, or open connections would hold up graceful shutdown. */
    @Override
    public void stop() {
        running = false;
        clients.forEach(client -> {
            remove(client);
            client.emitter.complete();
        });
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void run() {
        var sinceHeartbeat = Duration.ZERO;
        while (running) {
            try {
                Thread.sleep(FLUSH_INTERVAL);
                flush();
                sinceHeartbeat = sinceHeartbeat.plus(FLUSH_INTERVAL);
                if (sinceHeartbeat.compareTo(HEARTBEAT_INTERVAL) >= 0) {
                    sinceHeartbeat = Duration.ZERO;
                    // Also how a client that vanished without closing gets noticed.
                    clients.forEach(client -> client.offer(SseEmitter.event().comment("heartbeat")));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("Could not send live updates; trying again next round", e);
            }
        }
    }

    private void flush() {
        for (var channel : channels) {
            if (clients.isEmpty()) {
                channel.discard();
                continue;
            }
            channel.drain().ifPresent(batch -> {
                var id = batchId.incrementAndGet();
                clients.forEach(client -> client.offer(SseEmitter.event()
                        .id(String.valueOf(id))
                        .name(channel.name())
                        .data(batch, MediaType.APPLICATION_JSON)));
            });
        }
    }

    private void remove(Client client) {
        clients.remove(client);
        if (client.thread != null) {
            client.thread.interrupt();
        }
    }

    private final class Client {

        final SseEmitter emitter;
        final BlockingQueue<SseEmitter.SseEventBuilder> queue = new ArrayBlockingQueue<>(CLIENT_QUEUE_SIZE);
        volatile Thread thread;

        Client(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void offer(SseEmitter.SseEventBuilder event) {
            if (!queue.offer(event)) {
                log.info("Dropping a live client that can't keep up; it will reconnect and catch up");
                remove(this);
                emitter.complete();
            }
        }

        void sendQueued() {
            try {
                while (true) {
                    emitter.send(queue.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | IllegalStateException e) {
                remove(this);
            }
        }
    }
}
