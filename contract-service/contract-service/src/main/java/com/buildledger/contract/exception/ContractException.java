package com.buildledger.contract.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ContractException extends RuntimeException {
    private final HttpStatus status;
    public ContractException(String message, HttpStatus status) { super(message); this.status = status; }
}

