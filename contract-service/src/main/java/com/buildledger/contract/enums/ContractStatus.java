package com.buildledger.contract.enums;

public enum ContractStatus {

    DRAFT, PENDING, ACTIVE, COMPLETED, TERMINATED, EXPIRED, REJECTED;
 

    public boolean canTransitionTo(ContractStatus next) {
        return switch (this) {
            case DRAFT      -> next == PENDING || next == ACTIVE;
            case PENDING    -> false; // only vendor can move PENDING → ACTIVE/REJECTED via vendorRespondToContract
            case ACTIVE     -> next == COMPLETED || next == TERMINATED || next == EXPIRED;
            case COMPLETED  -> false;
            case TERMINATED -> false;
            case EXPIRED    -> false;
            case REJECTED   -> false;
        };
    }
}