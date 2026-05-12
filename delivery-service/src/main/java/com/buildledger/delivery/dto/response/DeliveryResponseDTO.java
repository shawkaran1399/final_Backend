package com.buildledger.delivery.dto.response;

import com.buildledger.delivery.enums.DeliveryStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeliveryResponseDTO {
    private Long          deliveryId;
    private Long          contractId;
    private LocalDate     date;
    private String        item;
    private BigDecimal    quantity;
    private String        unit;
    private String        remarks;
    private DeliveryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}