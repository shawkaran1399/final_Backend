package com.buildledger.contract.enums;

public enum ContractStatus {
    DRAFT, PENDING, ACTIVE, REJECTED, COMPLETED, TERMINATED, EXPIRED;

    public boolean canTransitionTo(ContractStatus next) {
        return switch (this) {
            case DRAFT      -> next == PENDING;
            case PENDING    -> next == ACTIVE || next == REJECTED;
            case ACTIVE     -> next == COMPLETED || next == TERMINATED || next == EXPIRED;
            case REJECTED   -> false;
            case COMPLETED  -> false;
            case TERMINATED -> false;
            case EXPIRED    -> false;
        };
    }
}

