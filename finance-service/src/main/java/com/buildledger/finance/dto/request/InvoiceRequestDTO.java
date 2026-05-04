package com.buildledger.finance.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceRequestDTO {
    @NotNull(message = "Contract ID is required") @Positive private Long contractId;
    @NotNull(message = "Amount is required") @DecimalMin("0.01") @Digits(integer=12, fraction=2) private BigDecimal amount;
    @NotNull(message = "Invoice date is required") @PastOrPresent private LocalDate date;
    @NotNull(message = "Due date is required") @Future(message = "Due date must be in the future") private LocalDate dueDate;
    @Size(max = 500, message = "Description too long") private String description;
}

