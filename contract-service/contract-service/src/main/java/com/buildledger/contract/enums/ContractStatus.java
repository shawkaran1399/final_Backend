package com.buildledger.contract.enums;

public enum ContractStatus {
    DRAFT, ACTIVE, COMPLETED, TERMINATED, EXPIRED;

    public boolean canTransitionTo(ContractStatus next) {
        return switch (this) {
            case DRAFT      -> next == ACTIVE;
            case ACTIVE     -> next == COMPLETED || next == TERMINATED || next == EXPIRED;
            case COMPLETED  -> false;
            case TERMINATED -> false;
            case EXPIRED    -> false;
        };
    }
}

