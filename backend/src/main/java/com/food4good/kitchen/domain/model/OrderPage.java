package com.food4good.kitchen.domain.model;

import java.util.List;

/** One page of the Order history, newest first. The cursor is null on the last page. */
public record OrderPage(List<Order> items, OrderCursor nextCursor) {
}
