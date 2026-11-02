package com.food4good.kitchen.infrastructure.configuration;

import com.food4good.kitchen.domain.ports.in.CreateRecipeUseCase;
import com.food4good.kitchen.domain.ports.in.ListRecipesUseCase;
import com.food4good.kitchen.domain.ports.in.UpdateRecipeUseCase;
import com.food4good.kitchen.domain.ports.out.RecipeRepositoryPort;
import com.food4good.kitchen.domain.service.CreateRecipeService;
import com.food4good.kitchen.domain.service.ListRecipesService;
import com.food4good.kitchen.domain.service.UpdateRecipeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Kitchen's domain services, which stay free of Spring annotations. */
@Configuration(proxyBeanMethods = false)
class KitchenConfiguration {

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
}
