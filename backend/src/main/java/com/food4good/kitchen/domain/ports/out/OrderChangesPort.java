package com.food4good.kitchen.domain.ports.out;

import java.util.Collection;
import java.util.UUID;

/** Announces that Orders changed. Within a transaction, it only takes effect if that transaction commits. */
public interface OrderChangesPort {

    void changed(Collection<UUID> orderIds);
}
