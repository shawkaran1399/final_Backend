package com.buildledger.delivery.exception;
import org.springframework.http.HttpStatus;
public class BadRequestException extends DeliveryException {
    public BadRequestException(String message) { super(message, HttpStatus.BAD_REQUEST); }
}

