package com.food4good.kitchen.infrastructure.adapters.in.rest;

import com.food4good.kitchen.domain.ports.in.CreateRecipeUseCase;
import com.food4good.kitchen.domain.ports.in.ListRecipesUseCase;
import com.food4good.kitchen.domain.ports.in.UpdateRecipeUseCase;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.RecipeRequest;
import com.food4good.kitchen.infrastructure.adapters.in.rest.dto.RecipeResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
class RecipeController {

    private final ListRecipesUseCase listRecipes;
    private final CreateRecipeUseCase createRecipe;
    private final UpdateRecipeUseCase updateRecipe;

    RecipeController(
            ListRecipesUseCase listRecipes, CreateRecipeUseCase createRecipe, UpdateRecipeUseCase updateRecipe) {
        this.listRecipes = listRecipes;
        this.createRecipe = createRecipe;
        this.updateRecipe = updateRecipe;
    }

    @GetMapping
    List<RecipeResponse> recipes() {
        return listRecipes.list().stream().map(RecipeResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<RecipeResponse> create(@RequestBody RecipeRequest request) {
        var recipe = createRecipe.create(request.toDetails());
        return ResponseEntity.created(URI.create("/api/recipes/" + recipe.id())).body(RecipeResponse.from(recipe));
    }

    @PutMapping("/{id}")
    RecipeResponse update(@PathVariable UUID id, @RequestBody RecipeRequest request) {
        return RecipeResponse.from(updateRecipe.update(id, request.toDetails()));
    }
}
