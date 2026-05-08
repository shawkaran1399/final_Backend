package com.buildledger.compliance.dto.response;

import com.buildledger.compliance.enums.ComplianceStatus;
import com.buildledger.compliance.enums.ComplianceType;
import lombok.*; import java.time.LocalDate; import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ComplianceRecordResponseDTO {
    private Long complianceId; private Long contractId; private ComplianceType type;
    private LocalDate date; private String notes;
    private ComplianceStatus status; private String reviewedBy; private LocalDateTime createdAt;
}

