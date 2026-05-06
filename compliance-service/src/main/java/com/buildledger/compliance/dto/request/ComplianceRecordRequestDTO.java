package com.buildledger.compliance.dto.request;

import com.buildledger.compliance.enums.ComplianceResult;
import com.buildledger.compliance.enums.ComplianceType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ComplianceRecordRequestDTO {
    @NotNull(message = "Contract ID is required")
    @Positive
    private Long contractId;
    @NotNull(message = "Compliance type is required")
    private ComplianceType type;
    private ComplianceResult result;
    @NotNull(message = "Date is required")
    @PastOrPresent
    private LocalDate date;
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
}

