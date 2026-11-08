package com.food4good.storage.infrastructure.configuration;

import com.food4good.kitchen.domain.ports.in.MarkOrderReadyUseCase;
import com.food4good.shared.domain.ports.out.TracingPort;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.BuyFromMarketUseCase;
import com.food4good.storage.domain.ports.in.ConsumeReservationUseCase;
import com.food4good.storage.domain.ports.in.ListPurchasesUseCase;
import com.food4good.storage.domain.ports.in.ReconcileStuckOrdersUseCase;
import com.food4good.storage.domain.ports.in.ListStockUseCase;
import com.food4good.storage.domain.ports.in.ProcessStorageCommandsUseCase;
import com.food4good.storage.domain.ports.in.RequestIngredientsUseCase;
import com.food4good.storage.domain.ports.out.FarmersMarketPort;
import com.food4good.storage.domain.ports.out.MarketJobRepositoryPort;
import com.food4good.storage.domain.ports.out.PurchaseChangesPort;
import com.food4good.storage.domain.ports.out.PurchaseRepositoryPort;
import com.food4good.storage.domain.ports.out.ReservationRepositoryPort;
import com.food4good.storage.domain.ports.out.StockChangesPort;
import com.food4good.storage.domain.ports.out.StockRepositoryPort;
import com.food4good.storage.domain.ports.out.StuckOrderRepositoryPort;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import com.food4good.storage.domain.service.BuyFromMarketService;
import com.food4good.storage.domain.service.ConsumeReservationService;
import com.food4good.storage.domain.service.ListPurchasesService;
import com.food4good.storage.domain.service.ListStockService;
import com.food4good.storage.domain.service.ReconcileStuckOrdersService;
import com.food4good.storage.domain.service.ProcessStorageCommandsService;
import com.food4good.storage.domain.service.RequestIngredientsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Food Storage's domain services, which stay free of Spring annotations. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MarketProperties.class, StorageProperties.class})
class StorageConfiguration {

    @Bean
    BuyFromMarketUseCase buyFromMarketUseCase(
            FarmersMarketPort market,
            MarketJobRepositoryPort jobs,
            StorageCommandRepositoryPort commands,
            TransactionPort transactions,
            TracingPort tracing) {
        return new BuyFromMarketService(market, jobs, commands, transactions, tracing);
    }

    @Bean
    ReconcileStuckOrdersUseCase reconcileStuckOrdersUseCase(
            StuckOrderRepositoryPort stuckOrders,
            StorageCommandRepositoryPort commands,
            TransactionPort transactions,
            TracingPort tracing) {
        return new ReconcileStuckOrdersService(stuckOrders, commands, transactions, tracing);
    }

    @Bean
    ListPurchasesUseCase listPurchasesUseCase(PurchaseRepositoryPort purchases) {
        return new ListPurchasesService(purchases);
    }

    @Bean
    RequestIngredientsUseCase requestIngredientsUseCase(StorageCommandRepositoryPort commands) {
        return new RequestIngredientsService(commands);
    }

    @Bean
    ConsumeReservationUseCase consumeReservationUseCase(StorageCommandRepositoryPort commands) {
        return new ConsumeReservationService(commands);
    }

    @Bean
    ListStockUseCase listStockUseCase(StockRepositoryPort stock) {
        return new ListStockService(stock);
    }

    @Bean
    ProcessStorageCommandsUseCase processStorageCommandsUseCase(
            StockRepositoryPort stock,
            ReservationRepositoryPort reservations,
            StorageCommandRepositoryPort commands,
            MarketJobRepositoryPort marketJobs,
            PurchaseRepositoryPort purchases,
            MarkOrderReadyUseCase markOrderReady,
            StockChangesPort stockChanges,
            PurchaseChangesPort purchaseChanges,
            TransactionPort transactions,
            TracingPort tracing) {
        return new ProcessStorageCommandsService(
                stock, reservations, commands, marketJobs, purchases, markOrderReady, stockChanges, purchaseChanges,
                transactions, tracing);
    }

    /** Read when metrics are published: how much work is queued right now. */
    @Bean
    MeterBinder storageMetrics(StorageCommandRepositoryPort commands, MarketJobRepositoryPort marketJobs) {
        return registry -> {
            Gauge.builder("storage.commands.pending", commands::countPending)
                    .description("Changes to Food Storage waiting for the stock writer")
                    .register(registry);
            Gauge.builder("market.jobs.pending", marketJobs::countPending)
                    .description("Shortfalls still being bought from the Farmers Market")
                    .register(registry);
        };
    }
}
