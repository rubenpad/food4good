package com.food4good.kitchen.infrastructure.adapters.in.rest;

import com.food4good.kitchen.domain.model.InvalidOrderRequestException;
import com.food4good.kitchen.domain.model.OrderQuery;
import com.food4good.kitchen.domain.model.OrderStatus;
import com.food4good.kitchen.domain.ports.in.ListOrdersUseCase;
import com.food4good.kitchen.domain.ports.in.PlaceOrdersUseCase;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.OrderPageResponse;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.OrderResponse;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.PlaceOrdersRequest;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.PlaceOrdersResponse;
import java.util.Arrays;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final PlaceOrdersUseCase placeOrders;
    private final ListOrdersUseCase listOrders;

    OrderController(PlaceOrdersUseCase placeOrders, ListOrdersUseCase listOrders) {
        this.placeOrders = placeOrders;
        this.listOrders = listOrders;
    }

    @PostMapping
    PlaceOrdersResponse place(@RequestBody PlaceOrdersRequest request) {
        if (request.quantity() == null) {
            throw new InvalidOrderRequestException("Quantity is required");
        }
        return PlaceOrdersResponse.from(placeOrders.place(request.quantity()));
    }

    @GetMapping
    OrderPageResponse history(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        var page = listOrders.list(new OrderQuery(parseStatus(status), OrderCursorCodec.decode(cursor), limit));
        return new OrderPageResponse(
                page.items().stream().map(OrderResponse::from).toList(),
                OrderCursorCodec.encode(page.nextCursor()));
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        return Arrays.stream(OrderStatus.values())
                .filter(candidate -> candidate.name().equals(status))
                .findFirst()
                .orElseThrow(() -> new InvalidOrderRequestException("Unknown status '%s'".formatted(status)));
    }
}
