package com.buildledger.contract.exception;
import org.springframework.http.HttpStatus;
public class BadRequestException extends ContractException {
    public BadRequestException(String message) { super(message, HttpStatus.BAD_REQUEST); }
}

