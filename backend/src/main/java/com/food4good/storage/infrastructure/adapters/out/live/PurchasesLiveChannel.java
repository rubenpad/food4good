package com.food4good.storage.infrastructure.adapters.out.live;

import com.food4good.shared.infrastructure.adapters.in.rest.LiveChannel;
import com.food4good.shared.infrastructure.adapters.out.live.AfterCommit;
import com.food4good.storage.domain.ports.out.PurchaseChangesPort;
import com.food4good.storage.domain.ports.out.PurchaseRepositoryPort;
import com.food4good.storage.infrastructure.adapters.in.rest.dto.PurchasePageResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Sends the Purchases made since the last batch. A Purchase never changes, so each is sent once. */
@Component
class PurchasesLiveChannel implements PurchaseChangesPort, LiveChannel {

    private final Set<Long> added = ConcurrentHashMap.newKeySet();
    private final PurchaseRepositoryPort purchases;

    PurchasesLiveChannel(PurchaseRepositoryPort purchases) {
        this.purchases = purchases;
    }

    @Override
    public void added(long purchaseId) {
        AfterCommit.run(() -> added.add(purchaseId));
    }

    @Override
    public String name() {
        return "purchases";
    }

    @Override
    public Optional<Object> drain() {
        var ids = takeAdded();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(purchases.findByIds(ids).stream().map(PurchasePageResponse.Item::from).toList());
        } catch (RuntimeException e) {
            added.addAll(ids);
            throw e;
        }
    }

    @Override
    public void discard() {
        added.clear();
    }

    private List<Long> takeAdded() {
        var ids = new ArrayList<Long>();
        for (var iterator = added.iterator(); iterator.hasNext(); ) {
            ids.add(iterator.next());
            iterator.remove();
        }
        return ids;
    }
}
