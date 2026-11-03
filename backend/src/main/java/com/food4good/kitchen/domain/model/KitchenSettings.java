package com.food4good.kitchen.domain.model;

import java.time.Duration;

/**
 * How the Kitchen runs. The scale shortens or stretches every preparation time, e.g. 0.01 for load
 * tests. Zero Cooks is allowed: Orders then stop at ready.
 */
public record KitchenSettings(int cooks, double preparationTimeScale) {

    public KitchenSettings {
        if (cooks < 0) {
            throw new IllegalArgumentException("The Kitchen can't have a negative number of Cooks");
        }
        if (preparationTimeScale <= 0) {
            throw new IllegalArgumentException("The preparation time scale must be positive");
        }
    }

    public Duration scaled(Duration preparationTime) {
        return Duration.ofMillis(Math.max(1, Math.round(preparationTime.toMillis() * preparationTimeScale)));
    }
}
