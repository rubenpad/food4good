package com.food4good.shared.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** One of the ten raw foods a Recipe can use, counted in whole units. */
public enum Ingredient {
    TOMATO,
    LEMON,
    POTATO,
    RICE,
    KETCHUP,
    LETTUCE,
    ONION,
    CHEESE,
    MEAT,
    CHICKEN;

    public static Ingredient fromId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }

    public static Optional<Ingredient> findById(String id) {
        return Arrays.stream(values()).filter(ingredient -> ingredient.id().equals(id)).findFirst();
    }

    /** The name the Farmers Market and the API use, e.g. {@code tomato}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
