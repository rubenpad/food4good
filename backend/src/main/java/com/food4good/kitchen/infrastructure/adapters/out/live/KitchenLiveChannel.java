package com.food4good.kitchen.infrastructure.adapters.out.live;

import com.food4good.kitchen.domain.ports.in.GetKitchenStatusUseCase;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.KitchenResponse;
import com.food4good.shared.infrastructure.adapters.in.rest.LiveChannel;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Sends the {@code /api/kitchen} snapshot after any Order changes. While Orders are preparing, the
 * Backlog shrinks with no event to say so, so it also looks again every second. Only a snapshot that
 * differs from the last one is sent.
 */
@Component
class KitchenLiveChannel implements LiveChannel {

    private static final Duration WHILE_PREPARING = Duration.ofSeconds(1);

    private final AtomicBoolean changed = new AtomicBoolean();
    private final GetKitchenStatusUseCase getStatus;

    // Guarded by this: a discard must not land in the middle of a drain and be overwritten.
    private KitchenResponse lastSeen;
    private long lastLookedAtNanos;

    KitchenLiveChannel(GetKitchenStatusUseCase getStatus) {
        this.getStatus = getStatus;
    }

    /** Called once an Order change has committed. */
    void changed() {
        changed.set(true);
    }

    @Override
    public String name() {
        return "kitchen";
    }

    @Override
    public synchronized Optional<Object> drain() {
        var orderChanged = changed.getAndSet(false);
        if (!orderChanged && !dueForALook()) {
            return Optional.empty();
        }
        KitchenResponse current;
        try {
            current = KitchenResponse.from(getStatus.status());
        } catch (RuntimeException e) {
            changed.compareAndSet(false, orderChanged);
            throw e;
        }
        lastLookedAtNanos = System.nanoTime();
        var previous = lastSeen;
        lastSeen = current;
        // A first look only learns whether Orders are preparing; a new client fetched the rest itself.
        if ((previous == null && !orderChanged) || current.equals(previous)) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    private boolean dueForALook() {
        if (lastSeen == null) {
            return true;
        }
        return lastSeen.ordersPreparing() > 0
                && System.nanoTime() - lastLookedAtNanos >= WHILE_PREPARING.toNanos();
    }

    @Override
    public synchronized void discard() {
        changed.set(false);
        lastSeen = null;
    }
}
