package com.food4good.storage.domain.model;

public class InvalidPurchaseQueryException extends RuntimeException {

    public InvalidPurchaseQueryException(String message) {
        super(message);
    }
}
