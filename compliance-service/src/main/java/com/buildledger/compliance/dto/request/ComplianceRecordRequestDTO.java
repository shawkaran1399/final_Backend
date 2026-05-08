package com.buildledger.compliance.dto.request;

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
    @NotNull(message = "Date is required")
    @FutureOrPresent(message = "Date must be today or a future date")
    private LocalDate date;
    @NotBlank(message = "Notes are required")
    @Size(min = 10, max = 1000, message = "Notes must be between 10 and 1000 characters")
    private String notes;
}

