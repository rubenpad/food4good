package com.food4good.storage.domain.ports.out;

import com.food4good.storage.domain.model.MarketJob;
import com.food4good.storage.domain.model.MarketShortfall;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface MarketJobRepositoryPort {

    void insertAll(List<MarketShortfall> shortfalls);

    /** Jobs still buying: waiting for their next attempt, or in one. */
    int countPending();

    /** Leases jobs that are due, or whose lease ran out because their call never reported back. */
    List<MarketJob> claimDue(int limit, Duration lease);

    /**
     * Records a sale, finishing the job once nothing remains.
     *
     * @return false if this attempt no longer owns the job, in which case nothing changed
     */
    boolean recordSale(MarketJob job, int sold);

    /** Records a sale of nothing, scheduling the next attempt. */
    void recordNothingSold(MarketJob job, Duration wait);

    /** Records a defect, and marks the job failed once it reaches {@code maxAttempts}. */
    void recordError(MarketJob job, String error, Duration wait, int maxAttempts);

    Optional<Duration> timeUntilNextDue();
}
