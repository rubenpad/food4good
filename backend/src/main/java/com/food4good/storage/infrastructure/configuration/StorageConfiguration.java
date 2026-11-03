package com.food4good.storage.infrastructure.configuration;

import com.food4good.kitchen.domain.ports.in.MarkOrderReadyUseCase;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.BuyFromMarketUseCase;
import com.food4good.storage.domain.ports.in.ConsumeReservationUseCase;
import com.food4good.storage.domain.ports.in.ListPurchasesUseCase;
import com.food4good.storage.domain.ports.in.ListStockUseCase;
import com.food4good.storage.domain.ports.in.ProcessStorageCommandsUseCase;
import com.food4good.storage.domain.ports.in.RequestIngredientsUseCase;
import com.food4good.storage.domain.ports.out.FarmersMarketPort;
import com.food4good.storage.domain.ports.out.MarketJobRepositoryPort;
import com.food4good.storage.domain.ports.out.PurchaseRepositoryPort;
import com.food4good.storage.domain.ports.out.ReservationRepositoryPort;
import com.food4good.storage.domain.ports.out.StockRepositoryPort;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import com.food4good.storage.domain.service.BuyFromMarketService;
import com.food4good.storage.domain.service.ConsumeReservationService;
import com.food4good.storage.domain.service.ListPurchasesService;
import com.food4good.storage.domain.service.ListStockService;
import com.food4good.storage.domain.service.ProcessStorageCommandsService;
import com.food4good.storage.domain.service.RequestIngredientsService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Food Storage's domain services, which stay free of Spring annotations. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketProperties.class)
class StorageConfiguration {

    @Bean
    BuyFromMarketUseCase buyFromMarketUseCase(
            FarmersMarketPort market,
            MarketJobRepositoryPort jobs,
            StorageCommandRepositoryPort commands,
            TransactionPort transactions) {
        return new BuyFromMarketService(market, jobs, commands, transactions);
    }

    @Bean
    ListPurchasesUseCase listPurchasesUseCase(PurchaseRepositoryPort purchases) {
        return new ListPurchasesService(purchases);
    }

    @Bean
    RequestIngredientsUseCase requestIngredientsUseCase(
            ReservationRepositoryPort reservations, StorageCommandRepositoryPort commands) {
        return new RequestIngredientsService(reservations, commands);
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
            TransactionPort transactions) {
        return new ProcessStorageCommandsService(
                stock, reservations, commands, marketJobs, purchases, markOrderReady, transactions);
    }
}
