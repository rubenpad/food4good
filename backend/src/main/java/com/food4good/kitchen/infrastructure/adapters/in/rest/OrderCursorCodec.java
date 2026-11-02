package com.food4good.kitchen.infrastructure.adapters.in.rest;

import com.food4good.kitchen.domain.model.InvalidOrderRequestException;
import com.food4good.kitchen.domain.model.OrderCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Turns a cursor into the opaque string clients pass back. */
final class OrderCursorCodec {

    private OrderCursorCodec() {
    }

    static String encode(OrderCursor cursor) {
        if (cursor == null) {
            return null;
        }
        var text = cursor.acceptedAt() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static OrderCursor decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            var text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            var parts = text.split("\\|", 2);
            return new OrderCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new InvalidOrderRequestException("That cursor is not valid");
        }
    }
}
