package com.buildledger.delivery.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends DeliveryException {
    public ServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}

