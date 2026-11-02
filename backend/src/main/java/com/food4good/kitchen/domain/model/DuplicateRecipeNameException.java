package com.food4good.kitchen.domain.model;

public class DuplicateRecipeNameException extends RuntimeException {

    public DuplicateRecipeNameException(String name) {
        super("A Recipe named '%s' already exists".formatted(name));
    }
}
