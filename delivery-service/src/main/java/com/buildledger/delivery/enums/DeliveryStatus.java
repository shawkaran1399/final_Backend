package com.buildledger.delivery.enums;

public enum DeliveryStatus {
    PENDING,
    MARKED_DELIVERED,
    ACCEPTED,
    REJECTED,
    DELAYED;

    public boolean canTransitionTo(DeliveryStatus next) {
        return switch (this) {
            case PENDING         -> next == MARKED_DELIVERED || next == DELAYED;
            case MARKED_DELIVERED -> next == ACCEPTED || next == REJECTED;
            case DELAYED         -> next == MARKED_DELIVERED;
            case ACCEPTED        -> false; // terminal
            case REJECTED        -> false; // terminal
        };
    }
}

