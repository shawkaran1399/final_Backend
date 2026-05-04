package com.buildledger.finance.dto.request;

import com.buildledger.finance.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentRequestDTO {
    @NotNull(message = "Invoice ID is required") @Positive private Long invoiceId;
    @NotNull(message = "Amount is required") @DecimalMin("0.01") @Digits(integer=12, fraction=2) private BigDecimal amount;
    @NotNull(message = "Payment date is required") @PastOrPresent private LocalDate date;
    @NotNull(message = "Payment method is required") private PaymentMethod method;
    @NotBlank(message = "Transaction reference is required")
    @Pattern(regexp = "^[A-Za-z0-9\\-/]+$", message = "Transaction reference must contain only letters, digits, hyphens, or slashes")
    private String transactionReference;
    @Size(max = 500, message = "Remarks too long") private String remarks;
}

