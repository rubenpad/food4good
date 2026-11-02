package com.food4good.kitchen.infrastructure.adapters.in.rest.dto;

import java.util.List;

public record OrderPageResponse(List<OrderResponse> items, String nextCursor) {
}
