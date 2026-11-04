package com.food4good.kitchen.domain.ports.out;

import com.food4good.kitchen.domain.model.PlacedOrders;

/** Waits its turn to be admitted, alongside whatever other requests arrive in the meantime. */
public interface AdmissionQueuePort {

    PlacedOrders admit(int quantity);
}
