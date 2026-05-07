package com.buildledger.delivery.exception;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends DeliveryException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(resource + " not found with " + field + ": " + value, HttpStatus.NOT_FOUND);
    }
}

