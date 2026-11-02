package com.food4good.kitchen.domain.ports.in;

import java.time.Duration;
import java.util.Optional;

/** One round of the Kitchen: delivers finished Dishes, then gives free Cooks the earliest-ready Orders. */
public interface CookOrdersUseCase {

    /** @return how long until the next Dish is due, if any is cooking */
    Optional<Duration> cook();
}
