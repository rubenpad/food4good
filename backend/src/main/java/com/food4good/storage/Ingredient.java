package com.food4good.storage;

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

    /** The name the Farmers Market and the API use, e.g. {@code tomato}. */
    public String id() {
        return name().toLowerCase();
    }
}
