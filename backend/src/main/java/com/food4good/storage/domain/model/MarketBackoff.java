package com.food4good.storage.domain.model;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** How long to wait after the Farmers Market sells nothing: doubling from 250 ms up to 5 s, ±20 %. */
public final class MarketBackoff {

    private static final long BASE_MILLIS = 250;
    private static final long CAP_MILLIS = 5_000;
    private static final int LAST_DOUBLING = 10;

    private MarketBackoff() {
    }

    public static Duration after(int zeroStreak) {
        var doublings = Math.clamp(zeroStreak - 1, 0, LAST_DOUBLING);
        var wait = Math.min(BASE_MILLIS << doublings, CAP_MILLIS);
        return Duration.ofMillis(Math.round(wait * ThreadLocalRandom.current().nextDouble(0.8, 1.2)));
    }
}
