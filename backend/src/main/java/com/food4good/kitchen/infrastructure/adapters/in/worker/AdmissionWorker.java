package com.food4good.kitchen.infrastructure.adapters.in.worker;

import com.food4good.kitchen.domain.model.AdmissionRequest;
import com.food4good.kitchen.domain.model.PlacedOrders;
import com.food4good.kitchen.domain.ports.in.AdmitOrdersUseCase;
import com.food4good.kitchen.domain.ports.out.AdmissionQueuePort;
import com.food4good.shared.domain.ports.out.TracingPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Admits every request that is waiting as one group, so a burst shares a few commits instead of
 * queueing for one each. It adds no delay: a lone request goes straight through, and requests only
 * group up while the previous group is committing.
 */
@Component
class AdmissionWorker implements AdmissionQueuePort, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AdmissionWorker.class);
    private static final int MAX_GROUP = 500;
    private static final long IDLE_CHECK_MILLIS = 100;
    /** Stops after the web server, so requests already being served can finish. */
    private static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;

    private record Waiting(AdmissionRequest request, CompletableFuture<PlacedOrders> outcome) {
    }

    private final AdmitOrdersUseCase admitOrders;
    private final TracingPort tracing;
    private final BlockingQueue<Waiting> queue = new LinkedBlockingQueue<>();

    private volatile boolean running;
    private Thread thread;

    AdmissionWorker(AdmitOrdersUseCase admitOrders, TracingPort tracing) {
        this.admitOrders = admitOrders;
        this.tracing = tracing;
    }

    @Override
    public PlacedOrders admit(int quantity) {
        if (!running) {
            throw new IllegalStateException("The Kitchen is shutting down");
        }
        // The request's trace context goes with it, since admission happens on another thread.
        var waiting = new Waiting(new AdmissionRequest(quantity, tracing.currentContext()), new CompletableFuture<>());
        queue.add(waiting);
        try {
            return waiting.outcome().join();
        } catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException cause ? cause : e;
        }
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofVirtual().name("admission").start(this::run);
    }

    @Override
    public void stop() {
        running = false;
        try {
            thread.join(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var left = new ArrayList<Waiting>();
        queue.drainTo(left);
        left.forEach(waiting -> waiting.outcome().completeExceptionally(new IllegalStateException("The Kitchen is shutting down")));
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    private void run() {
        while (running) {
            try {
                var first = queue.poll(IDLE_CHECK_MILLIS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    var group = new ArrayList<>(List.of(first));
                    queue.drainTo(group, MAX_GROUP - 1);
                    admit(group);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void admit(List<Waiting> group) {
        try {
            var outcomes = admitOrders.admit(group.stream().map(Waiting::request).toList());
            for (var i = 0; i < group.size(); i++) {
                group.get(i).outcome().complete(outcomes.get(i));
            }
        } catch (RuntimeException e) {
            log.error("Could not admit a group of {} requests", group.size(), e);
            group.forEach(waiting -> waiting.outcome().completeExceptionally(e));
        }
    }
}
