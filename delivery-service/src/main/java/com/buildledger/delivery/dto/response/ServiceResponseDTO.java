package com.buildledger.delivery.dto.response;

import com.buildledger.delivery.enums.ServiceStatus;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ServiceResponseDTO {
    private Long          serviceId;
    private Long          contractId;
    private String        description;
    private LocalDate     completionDate;
    private ServiceStatus status;
    private String        remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}