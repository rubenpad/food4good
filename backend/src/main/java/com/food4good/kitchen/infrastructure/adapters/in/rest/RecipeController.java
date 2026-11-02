package com.food4good.kitchen.infrastructure.adapters.in.rest;

import com.food4good.kitchen.domain.ports.in.ListRecipesUseCase;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.RecipeResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
class RecipeController {

    private final ListRecipesUseCase listRecipes;

    RecipeController(ListRecipesUseCase listRecipes) {
        this.listRecipes = listRecipes;
    }

    @GetMapping
    List<RecipeResponse> recipes() {
        return listRecipes.list().stream().map(RecipeResponse::from).toList();
    }
}
