package com.food4good.kitchen.domain.model;

/** A request to place {@code quantity} Orders, waiting to be admitted. {@code cause} is the trace context of the request. */
public record AdmissionRequest(int quantity, String cause) {
}
