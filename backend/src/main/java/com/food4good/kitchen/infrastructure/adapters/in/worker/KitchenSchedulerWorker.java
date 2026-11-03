package com.food4good.kitchen.infrastructure.adapters.in.worker;

import com.food4good.kitchen.domain.ports.in.CookOrdersUseCase;
import com.food4good.shared.infrastructure.adapters.in.worker.SingleInstanceWorker;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Assigns Cooks and delivers finished Dishes, waking when an Order becomes ready or a Dish is due. */
@Component
class KitchenSchedulerWorker extends SingleInstanceWorker {

    private static final long LOCK_KEY = 4_004_002L;

    private final CookOrdersUseCase cookOrders;

    KitchenSchedulerWorker(DataSource dataSource, CookOrdersUseCase cookOrders) {
        super(dataSource, "kitchen scheduler", LOCK_KEY, "kitchen_orders");
        this.cookOrders = cookOrders;
    }

    @Override
    protected Duration runOnce() {
        return cookOrders.cook().orElse(Duration.ofSeconds(1));
    }
}
