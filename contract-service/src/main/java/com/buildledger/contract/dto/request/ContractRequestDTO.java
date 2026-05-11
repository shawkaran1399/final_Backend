package com.buildledger.contract.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Data
public class ContractRequestDTO {

    @NotNull(message = "Vendor ID is required")
    @Positive(message = "Vendor ID must be positive")
    private Long vendorId;

    @NotNull(message = "Project ID is required")
    @Positive(message = "Project ID must be positive")
    private Long projectId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Contract value is required")
    @DecimalMin(value = "0.01", message = "Contract value must be greater than zero")
    @Digits(integer = 14, fraction = 2, message = "Invalid value format")
    private BigDecimal value;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Valid
    private List<ContractTermRequestDTO> terms;
}

