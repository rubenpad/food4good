package com.food4good.storage.domain.ports.out;

import com.food4good.shared.domain.model.Ingredient;

public interface FarmersMarketPort {

    /**
     * Makes one buy attempt. The market decides how much it sells.
     *
     * @return the units sold; 0 when it sold none or the call failed in any way
     */
    int buy(Ingredient ingredient);
}
