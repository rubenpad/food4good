package com.food4good.storage.domain.service;

import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.model.MarketBackoff;
import com.food4good.storage.domain.model.MarketJob;
import com.food4good.storage.domain.ports.in.BuyFromMarketUseCase;
import com.food4good.storage.domain.ports.out.FarmersMarketPort;
import com.food4good.storage.domain.ports.out.MarketJobRepositoryPort;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyFromMarketService implements BuyFromMarketUseCase {

    private static final Logger log = LoggerFactory.getLogger(BuyFromMarketService.class);
    /** Longer than any call can take, so a lease only runs out when an instance has died. */
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final int MAX_ERRORS = 5;

    private final FarmersMarketPort market;
    private final MarketJobRepositoryPort jobs;
    private final StorageCommandRepositoryPort commands;
    private final TransactionPort transactions;

    public BuyFromMarketService(
            FarmersMarketPort market,
            MarketJobRepositoryPort jobs,
            StorageCommandRepositoryPort commands,
            TransactionPort transactions) {
        this.market = market;
        this.jobs = jobs;
        this.commands = commands;
        this.transactions = transactions;
    }

    @Override
    public List<MarketJob> claimDue(int limit) {
        return jobs.claimDue(limit, LEASE);
    }

    @Override
    public void attempt(MarketJob job) {
        try {
            // No connection or lock is held during the call.
            var sold = market.buy(job.ingredient());
            transactions.inTransaction(() -> record(job, sold));
        } catch (RuntimeException e) {
            log.error("Market job {} failed", job, e);
            jobs.recordError(job, e.toString(), MarketBackoff.after(job.zeroStreak() + 1), MAX_ERRORS);
        }
    }

    private void record(MarketJob job, int sold) {
        if (sold == 0) {
            jobs.recordNothingSold(job, MarketBackoff.after(job.zeroStreak() + 1));
        } else if (jobs.recordSale(job, sold)) {
            commands.enqueuePurchase(job, sold);
        }
    }

    @Override
    public Optional<Duration> timeUntilNextDue() {
        return jobs.timeUntilNextDue();
    }
}
