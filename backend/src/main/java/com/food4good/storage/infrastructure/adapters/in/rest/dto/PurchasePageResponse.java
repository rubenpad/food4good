package com.food4good.storage.infrastructure.adapters.in.rest.dto;

import com.food4good.storage.domain.model.Purchase;
import com.food4good.storage.domain.model.PurchasePage;
import java.time.Instant;
import java.util.List;

public record PurchasePageResponse(List<Item> items, String nextCursor) {

    public static PurchasePageResponse from(PurchasePage page) {
        return new PurchasePageResponse(
                page.items().stream().map(Item::from).toList(),
                page.nextCursor() == null ? null : String.valueOf(page.nextCursor()));
    }

    public record Item(long id, String ingredient, int quantity, Instant purchasedAt) {

        static Item from(Purchase purchase) {
            return new Item(purchase.id(), purchase.ingredient().id(), purchase.quantity(), purchase.purchasedAt());
        }
    }
}
