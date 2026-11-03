package com.food4good.kitchen.domain.model;

import java.time.Duration;

/**
 * How the Kitchen runs. The scale shortens or stretches every preparation time, e.g. 0.01 for load
 * tests. Zero Cooks is allowed: Orders then stop at ready, and estimates assume one Cook, because a
 * Kitchen with no capacity at all could never accept the Orders that setting exists to hold.
 */
public record KitchenSettings(int cooks, Duration maxWait, double preparationTimeScale) {

    public KitchenSettings {
        if (cooks < 0) {
            throw new IllegalArgumentException("The Kitchen can't have a negative number of Cooks");
        }
        if (maxWait == null || !maxWait.isPositive()) {
            throw new IllegalArgumentException("The Maximum wait must be positive");
        }
        if (preparationTimeScale <= 0) {
            throw new IllegalArgumentException("The preparation time scale must be positive");
        }
    }

    public Duration scaled(Duration preparationTime) {
        return Duration.ofMillis(Math.max(1, Math.round(preparationTime.toMillis() * preparationTimeScale)));
    }

    /** The most preparation work the Backlog may hold: what the Cooks can clear within the Maximum wait. */
    public Duration capacity() {
        return maxWait.multipliedBy(countedCooks());
    }

    /** How long the Cooks take to clear this much preparation work. */
    public Duration timeToClear(Duration work) {
        return work.dividedBy(countedCooks());
    }

    private int countedCooks() {
        return Math.max(cooks, 1);
    }
}
