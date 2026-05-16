package com.buildledger.contract.dto.response;

import com.buildledger.contract.enums.ContractStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ContractResponseDTO {
    private Long contractId;
    private Long vendorId;
    private String vendorName;
    private String vendorUsername;
    private Long projectId;
    private String projectName;
    private String managerUsername;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal value;
    private ContractStatus status;
    private String description;
    private String vendorRemarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}