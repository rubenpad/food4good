package com.food4good.market;

/**
 * The Farmers Market, as Food Storage sees it. Takes the Ingredient's name rather than the type so
 * that this module does not depend on Food Storage, which depends on it.
 */
public interface FarmersMarket {

    /**
     * Makes one buy attempt for the named Ingredient (e.g. {@code tomato}).
     *
     * @return the units sold; 0 when the market sold none or the call failed
     */
    int buy(String ingredient);
}
