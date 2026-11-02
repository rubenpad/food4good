package com.food4good.storage.domain.model;

import com.food4good.shared.domain.model.Ingredient;
import java.util.UUID;

/** The units set aside for one Order of one Ingredient. Its quantity never changes after acceptance. */
public record ReservationLine(UUID orderId, Ingredient ingredient, int needed, int reserved, boolean consumed) {

    public int shortfall() {
        return needed - reserved;
    }

    public boolean isFilled() {
        return shortfall() == 0;
    }

    public ReservationLine fill(int units) {
        return new ReservationLine(orderId, ingredient, needed, reserved + units, consumed);
    }

    public ReservationLine consume() {
        return new ReservationLine(orderId, ingredient, needed, reserved, true);
    }
}
