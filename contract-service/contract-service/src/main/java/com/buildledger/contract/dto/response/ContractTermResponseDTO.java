package com.buildledger.contract.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ContractTermResponseDTO {
    private Long termId;
    private Long contractId;
    private String description;
    private Boolean complianceFlag;
    private Integer sequenceNumber;
    private LocalDateTime createdAt;
}

