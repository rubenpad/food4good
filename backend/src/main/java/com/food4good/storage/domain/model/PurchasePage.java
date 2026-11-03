package com.food4good.storage.domain.model;

import java.util.List;

/** One page of the Purchase history, newest first. The cursor is null on the last page. */
public record PurchasePage(List<Purchase> items, Long nextCursor) {
}
