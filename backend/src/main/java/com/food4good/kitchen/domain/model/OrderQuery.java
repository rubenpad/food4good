package com.food4good.kitchen.domain.model;

/** What to read from the Order history. Status and cursor are optional. */
public record OrderQuery(OrderStatus status, OrderCursor cursor, int limit) {

    public static final int MAX_LIMIT = 200;

    public OrderQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidOrderRequestException("limit must be between 1 and %d".formatted(MAX_LIMIT));
        }
    }
}
