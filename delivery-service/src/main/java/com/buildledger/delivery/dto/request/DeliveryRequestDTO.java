package com.buildledger.delivery.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DeliveryRequestDTO {

    @NotNull(message = "Contract ID is required")
    @Positive(message = "Contract ID must be a positive number")
    private Long contractId;

    @NotNull(message = "Delivery date is required")
    //@PastOrPresent(message = "Delivery date cannot be in the future")
    private LocalDate date;

    @NotBlank(message = "Item name is required")
    @Size(min = 2, max = 200, message = "Item name must be between 2 and 200 characters")
    private String item;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    @Digits(integer = 14, fraction = 2, message = "Invalid quantity format")
    private BigDecimal quantity;

    @NotBlank(message = "Unit is required")
    @Size(max = 50, message = "Unit cannot exceed 50 characters")
    private String unit;

    @Size(max = 500, message = "Remarks cannot exceed 500 characters")
    private String remarks;
}

