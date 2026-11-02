package com.food4good.kitchen.domain.ports.in;

import com.food4good.kitchen.domain.model.KitchenStatus;

public interface GetKitchenStatusUseCase {

    KitchenStatus status();
}
