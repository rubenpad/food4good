package com.food4good.kitchen.infrastructure.adapters.in.rest;

import com.food4good.kitchen.domain.ports.in.GetKitchenStatusUseCase;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.KitchenResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchen")
class KitchenController {

    private final GetKitchenStatusUseCase getStatus;

    KitchenController(GetKitchenStatusUseCase getStatus) {
        this.getStatus = getStatus;
    }

    @GetMapping
    KitchenResponse status() {
        return KitchenResponse.from(getStatus.status());
    }
}
