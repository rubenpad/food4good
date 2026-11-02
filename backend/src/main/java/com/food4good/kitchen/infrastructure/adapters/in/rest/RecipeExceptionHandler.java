package com.food4good.kitchen.infrastructure.adapters.in.rest;

import com.food4good.kitchen.domain.model.DuplicateRecipeNameException;
import com.food4good.kitchen.domain.model.InvalidRecipeException;
import com.food4good.kitchen.domain.model.RecipeNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Reports Recipe errors as RFC 9457 problem details, with the message for the Manager in {@code detail}. */
@RestControllerAdvice(assignableTypes = RecipeController.class)
class RecipeExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidRecipeException.class)
    ProblemDetail invalid(InvalidRecipeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DuplicateRecipeNameException.class)
    ProblemDetail duplicateName(DuplicateRecipeNameException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RecipeNotFoundException.class)
    ProblemDetail notFound(RecipeNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(
                status, "The request body must be valid JSON, with whole numbers for quantities and preparation time");
        return handleExceptionInternal(e, problem, headers, status, request);
    }
}
