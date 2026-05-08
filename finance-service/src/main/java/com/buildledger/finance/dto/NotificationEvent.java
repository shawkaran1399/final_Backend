package com.buildledger.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka message payload — published by other services and consumed here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String recipientEmail;
    private String recipientName;
    private String type;
    private String subject;
    private String message;
    private String referenceId; // e.g., vendorId, contractId, invoiceId
    private String referenceType; // e.g., "VENDOR", "CONTRACT", "INVOICE"
}