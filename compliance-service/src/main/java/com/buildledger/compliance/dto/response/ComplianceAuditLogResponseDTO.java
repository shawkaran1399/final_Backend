package com.buildledger.compliance.dto.response;

import lombok.*; import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ComplianceAuditLogResponseDTO {
    private Long logId;
    private Long complianceRecordId;
    private String action;
    private String performedBy;
    private String findings;
    private LocalDateTime createdAt;
}
