package com.food4good.kitchen.infrastructure.adapters.out.live;

import com.food4good.kitchen.domain.ports.out.OrderChangesPort;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.OrderResponse;
import com.food4good.shared.infrastructure.adapters.in.rest.LiveChannel;
import com.food4good.shared.infrastructure.adapters.out.live.AfterCommit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Sends the current state of every Order that changed since the last batch, and tells the Kitchen snapshot to refresh. */
@Component
class OrdersLiveChannel implements OrderChangesPort, LiveChannel {

    private final Set<UUID> changed = ConcurrentHashMap.newKeySet();
    private final OrderRepositoryPort orders;
    private final KitchenLiveChannel kitchen;

    OrdersLiveChannel(OrderRepositoryPort orders, KitchenLiveChannel kitchen) {
        this.orders = orders;
        this.kitchen = kitchen;
    }

    @Override
    public void changed(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            // The Kitchen scheduler reports every tick, usually with nothing delivered.
            return;
        }
        var ids = List.copyOf(orderIds);
        AfterCommit.run(() -> {
            changed.addAll(ids);
            kitchen.changed();
        });
    }

    @Override
    public String name() {
        return "orders";
    }

    @Override
    public Optional<Object> drain() {
        var ids = takeChanged();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(orders.findByIds(ids).stream().map(OrderResponse::from).toList());
        } catch (RuntimeException e) {
            changed.addAll(ids);
            throw e;
        }
    }

    @Override
    public void discard() {
        changed.clear();
    }

    private List<UUID> takeChanged() {
        var ids = new ArrayList<UUID>();
        for (var iterator = changed.iterator(); iterator.hasNext(); ) {
            ids.add(iterator.next());
            iterator.remove();
        }
        return ids;
    }
}
