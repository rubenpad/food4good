package com.food4good.kitchen.infrastructure.configuration;

import com.food4good.kitchen.domain.model.KitchenSettings;
import com.food4good.kitchen.domain.ports.in.CookOrdersUseCase;
import com.food4good.kitchen.domain.ports.in.CreateRecipeUseCase;
import com.food4good.kitchen.domain.ports.in.GetKitchenStatusUseCase;
import com.food4good.kitchen.domain.ports.in.ListOrdersUseCase;
import com.food4good.kitchen.domain.ports.in.ListRecipesUseCase;
import com.food4good.kitchen.domain.ports.in.MarkOrderReadyUseCase;
import com.food4good.kitchen.domain.ports.in.PlaceOrdersUseCase;
import com.food4good.kitchen.domain.ports.in.UpdateRecipeUseCase;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import com.food4good.kitchen.domain.service.CookOrdersService;
import com.food4good.kitchen.domain.service.CreateRecipeService;
import com.food4good.kitchen.domain.service.GetKitchenStatusService;
import com.food4good.kitchen.domain.service.ListOrdersService;
import com.food4good.kitchen.domain.service.ListRecipesService;
import com.food4good.kitchen.domain.service.MarkOrderReadyService;
import com.food4good.kitchen.domain.service.PlaceOrdersService;
import com.food4good.kitchen.domain.service.UpdateRecipeService;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.ports.in.ConsumeReservationUseCase;
import com.food4good.storage.domain.ports.in.RequestIngredientsUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Kitchen's domain services, which stay free of Spring annotations. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KitchenProperties.class)
class KitchenConfiguration {

    @Bean
    KitchenSettings kitchenSettings(KitchenProperties properties) {
        return new KitchenSettings(properties.cooks(), properties.maxWait(), properties.preparationTimeScale());
    }

    @Bean
    ListRecipesUseCase listRecipesUseCase(RecipeRepositoryPort recipes) {
        return new ListRecipesService(recipes);
    }

    @Bean
    CreateRecipeUseCase createRecipeUseCase(RecipeRepositoryPort recipes) {
        return new CreateRecipeService(recipes);
    }

    @Bean
    UpdateRecipeUseCase updateRecipeUseCase(RecipeRepositoryPort recipes) {
        return new UpdateRecipeService(recipes);
    }

    @Bean
    PlaceOrdersUseCase placeOrdersUseCase(
            RecipeRepositoryPort recipes,
            OrderRepositoryPort orders,
            RequestIngredientsUseCase requestIngredients,
            TransactionPort transactions,
            KitchenSettings settings) {
        return new PlaceOrdersService(recipes, orders, requestIngredients, transactions, settings);
    }

    @Bean
    CookOrdersUseCase cookOrdersUseCase(
            OrderRepositoryPort orders,
            ConsumeReservationUseCase consumeReservation,
            TransactionPort transactions,
            KitchenSettings settings) {
        return new CookOrdersService(orders, consumeReservation, transactions, settings);
    }

    @Bean
    GetKitchenStatusUseCase getKitchenStatusUseCase(
            OrderRepositoryPort orders, RecipeRepositoryPort recipes, KitchenSettings settings) {
        return new GetKitchenStatusService(orders, recipes, settings);
    }

    @Bean
    ListOrdersUseCase listOrdersUseCase(OrderRepositoryPort orders) {
        return new ListOrdersService(orders);
    }

    @Bean
    MarkOrderReadyUseCase markOrderReadyUseCase(OrderRepositoryPort orders) {
        return new MarkOrderReadyService(orders);
    }
}
