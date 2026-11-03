package com.food4good.storage.domain.ports.in;

import com.food4good.storage.domain.model.MarketJob;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Buys what Reservations lack. Attempts are slow, so they run outside the single stock writer. */
public interface BuyFromMarketUseCase {

    /** Takes up to {@code limit} jobs that are due, leasing them to the caller. */
    List<MarketJob> claimDue(int limit);

    /** Makes one call to the Farmers Market and records the outcome. */
    void attempt(MarketJob job);

    /** How long until the next job is due, if any is waiting. */
    Optional<Duration> timeUntilNextDue();
}
