package com.food4good.kitchen.domain.ports.in;

import com.food4good.kitchen.domain.model.AdmissionRequest;
import com.food4good.kitchen.domain.model.PlacedOrders;
import java.util.List;

/**
 * Admits a group of requests to place Orders in one transaction, in order, so they share one commit
 * instead of queueing for one each. Only the admission worker calls this.
 */
public interface AdmitOrdersUseCase {

    /** @return one outcome per request, in the same order */
    List<PlacedOrders> admit(List<AdmissionRequest> requests);
}
