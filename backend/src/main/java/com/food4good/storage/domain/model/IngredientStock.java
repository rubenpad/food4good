package com.food4good.storage.domain.model;

import com.food4good.shared.domain.model.Ingredient;

/** One Ingredient in Food Storage: every unit on hand, and the part of it held by Reservations. */
public record IngredientStock(Ingredient ingredient, int onHand, int reserved) {

    public int available() {
        return onHand - reserved;
    }

    public IngredientStock reserve(int units) {
        return new IngredientStock(ingredient, onHand, reserved + units);
    }

    /** Adds bought units to what is on hand. They count as available until reserved. */
    public IngredientStock receive(int units) {
        return new IngredientStock(ingredient, onHand + units, reserved);
    }

    /** Takes reserved units out of Food Storage. */
    public IngredientStock consume(int units) {
        return new IngredientStock(ingredient, onHand - units, reserved - units);
    }
}
