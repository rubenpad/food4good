package com.food4good.storage.infrastructure.adapters.in.worker;

import com.food4good.shared.infrastructure.adapters.in.worker.SingleInstanceWorker;
import com.food4good.storage.domain.ports.in.BuyFromMarketUseCase;
import com.food4good.storage.infrastructure.configuration.MarketProperties;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Hands due market jobs to virtual threads, one call each. Running on one instance makes the limit
 * on concurrent calls a limit for the whole system. Finished calls NOTIFY, which wakes this to use
 * the freed slot.
 */
@Component
class MarketBuyerWorker extends SingleInstanceWorker {

    private static final long LOCK_KEY = 4_004_003L;
    private static final Duration POLL = Duration.ofSeconds(1);

    private final BuyFromMarketUseCase buyFromMarket;
    private final Semaphore callSlots;
    private final int maxCalls;

    MarketBuyerWorker(DataSource dataSource, BuyFromMarketUseCase buyFromMarket, MarketProperties properties) {
        super(dataSource, "market buyer", LOCK_KEY, "market_jobs");
        this.buyFromMarket = buyFromMarket;
        this.maxCalls = properties.maxConcurrentCalls();
        this.callSlots = new Semaphore(maxCalls);
    }

    @Override
    protected Duration runOnce() {
        var free = callSlots.availablePermits();
        if (free == 0) {
            return POLL;
        }
        for (var job : buyFromMarket.claimDue(free)) {
            callSlots.acquireUninterruptibly();
            Thread.ofVirtual().name("market-call-", job.id()).start(() -> {
                try {
                    buyFromMarket.attempt(job);
                } finally {
                    callSlots.release();
                }
            });
        }
        return buyFromMarket.timeUntilNextDue().orElse(POLL);
    }

    /** Lets calls in progress record their results while the database is still reachable. */
    @Override
    public void stop() {
        super.stop();
        try {
            if (callSlots.tryAcquire(maxCalls, 5, TimeUnit.SECONDS)) {
                callSlots.release(maxCalls);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
