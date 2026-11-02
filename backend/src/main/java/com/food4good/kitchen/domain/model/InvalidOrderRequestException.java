package com.food4good.kitchen.domain.model;

public class InvalidOrderRequestException extends RuntimeException {

    public InvalidOrderRequestException(String message) {
        super(message);
    }
}
