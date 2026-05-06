package com.buildledger.delivery.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String referenceId;
    private String referenceType;
}