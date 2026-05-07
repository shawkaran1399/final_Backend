package com.buildledger.contract.dto.response;

import com.buildledger.contract.enums.ProjectStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProjectResponseDTO {
    private Long projectId;
    private String name;
    private String description;
    private String location;
    private BigDecimal budget;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualEndDate;
    private ProjectStatus status;
    private Long managerId;
    private String managerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

