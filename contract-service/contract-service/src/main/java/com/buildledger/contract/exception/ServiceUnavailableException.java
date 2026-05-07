package com.buildledger.contract.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ContractException {
    public ServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}

