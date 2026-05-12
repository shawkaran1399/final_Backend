package com.buildledger.delivery.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ServiceRequestDTO {

    @NotNull(message = "Contract ID is required")
    @Positive(message = "Contract ID must be a positive number")
    private Long contractId;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters")
    private String description;

    @NotNull(message = "Expected completion date is required")
    // Date must be within contract period — validated in ServiceTrackingServiceImpl
    private LocalDate completionDate;

    /**
     * Price of this service in INR.
     * Backend validates: price <= contractValue - sum(existing delivery+service prices)
     */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 14, fraction = 2, message = "Invalid price format")
    private BigDecimal price;

    @Size(max = 250, message = "Remarks cannot exceed 250 characters")
    private String remarks;
}