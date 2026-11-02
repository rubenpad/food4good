package com.food4good.kitchen.domain.model;

public class InvalidRecipeException extends RuntimeException {

    public InvalidRecipeException(String message) {
        super(message);
    }
}
