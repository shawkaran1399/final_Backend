package com.buildledger.compliance.enums;

public enum ComplianceStatus {
    PENDING, PASSED, FAILED;

    public boolean canTransitionTo(ComplianceStatus next) {
        return switch (this) {
            case PENDING -> next == PASSED || next == FAILED;
            case PASSED  -> false;
            case FAILED  -> false;
        };
    }
}

