package com.buildledger.delivery.enums;

public enum ServiceStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    VERIFIED,
    UNVERIFIED;

    public boolean canTransitionTo(ServiceStatus next) {
        return switch (this) {
            case PENDING     -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED   -> next == VERIFIED || next == UNVERIFIED;
            case VERIFIED    -> false; // terminal
            case UNVERIFIED  -> false; // terminal
        };
    }
}

