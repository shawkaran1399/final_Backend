package com.buildledger.delivery.enums;

public enum ServiceStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    VERIFIED;

    public boolean canTransitionTo(ServiceStatus next) {
        return switch (this) {
            case PENDING     -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED   -> next == VERIFIED;
            case VERIFIED    -> false; // terminal
        };
    }
}

