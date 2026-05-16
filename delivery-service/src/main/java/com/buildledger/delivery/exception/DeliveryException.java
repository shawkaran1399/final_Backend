package com.buildledger.delivery.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DeliveryException extends RuntimeException {
    private final HttpStatus status;
    public DeliveryException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}

