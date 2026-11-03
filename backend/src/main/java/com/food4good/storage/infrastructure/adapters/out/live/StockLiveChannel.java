package com.food4good.storage.infrastructure.adapters.out.live;

import com.food4good.shared.infrastructure.adapters.in.rest.LiveChannel;
import com.food4good.shared.infrastructure.adapters.out.live.AfterCommit;
import com.food4good.storage.domain.ports.out.StockChangesPort;
import com.food4good.storage.domain.ports.out.StockRepositoryPort;
import com.food4good.storage.infrastructure.adapters.in.rest.dto.StockResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Sends all of Food Storage whenever any of it changed. It is only ten rows. */
@Component
class StockLiveChannel implements StockChangesPort, LiveChannel {

    private final AtomicBoolean changed = new AtomicBoolean();
    private final StockRepositoryPort stock;

    StockLiveChannel(StockRepositoryPort stock) {
        this.stock = stock;
    }

    @Override
    public void changed() {
        AfterCommit.run(() -> changed.set(true));
    }

    @Override
    public String name() {
        return "storage";
    }

    @Override
    public Optional<Object> drain() {
        if (!changed.getAndSet(false)) {
            return Optional.empty();
        }
        try {
            return Optional.of(stock.findAll().stream().map(StockResponse::from).toList());
        } catch (RuntimeException e) {
            changed.set(true);
            throw e;
        }
    }

    @Override
    public void discard() {
        changed.set(false);
    }
}
