package com.buildledger.contract.enums;

public enum ProjectStatus {
    PLANNING,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(ProjectStatus next) {
        return switch (this) {
            case PLANNING  -> next == ACTIVE || next == CANCELLED;
            case ACTIVE    -> next == ON_HOLD || next == COMPLETED || next == CANCELLED;
            case ON_HOLD   -> next == ACTIVE || next == CANCELLED;
            case COMPLETED -> false;
            case CANCELLED -> false;
        };
    }
}

