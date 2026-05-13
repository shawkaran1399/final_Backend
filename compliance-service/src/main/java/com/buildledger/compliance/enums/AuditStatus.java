package com.buildledger.compliance.enums;

public enum AuditStatus {
    IN_PROGRESS, PENDING_REVIEW, COMPLETED, CANCELLED;

    public boolean canTransitionTo(AuditStatus next) {
        return switch (this) {
            case IN_PROGRESS    -> next == PENDING_REVIEW || next == CANCELLED;
            case PENDING_REVIEW -> next == COMPLETED || next == CANCELLED;
            case COMPLETED      -> false;
            case CANCELLED      -> false;
        };
    }
}

