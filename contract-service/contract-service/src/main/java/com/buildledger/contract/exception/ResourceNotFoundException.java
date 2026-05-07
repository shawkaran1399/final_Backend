package com.buildledger.contract.exception;
import org.springframework.http.HttpStatus;
public class ResourceNotFoundException extends ContractException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(resource + " not found with " + field + ": " + value, HttpStatus.NOT_FOUND);
    }
}

