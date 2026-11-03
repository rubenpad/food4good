package com.food4good.storage.infrastructure.adapters.in.rest;

import com.food4good.storage.domain.model.InvalidPurchaseQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Reports Food Storage's errors as RFC 9457 problem details, with the message in {@code detail}. */
@RestControllerAdvice(assignableTypes = PurchaseController.class)
class StorageExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidPurchaseQueryException.class)
    ProblemDetail invalidQuery(InvalidPurchaseQueryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
