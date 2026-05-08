package com.buildledger.compliance.enums;
public enum ComplianceType {
    DELIVERY_CHECK, SERVICE_CHECK;

    public boolean isReferenceCheck() {
        return this == DELIVERY_CHECK || this == SERVICE_CHECK;
    }
}

