package com.buildledger.contract.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProjectRequestDTO {

    @NotBlank(message = "Project name is required")
    @Size(min = 3, max = 100, message = "Project name must be between 3 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private String location;

    @DecimalMin(value = "0.0", inclusive = false, message = "Budget must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Budget format is invalid")
    private BigDecimal budget;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualEndDate;

    @NotNull(message = "Manager ID is required")
    @Positive(message = "Manager ID must be a positive number")
    private Long managerId;

    private String managerName;
}