package com.buildledger.contract.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ContractTermRequestDTO {

    @NotBlank(message = "Description is required")
    @Size(min = 5, max = 1000, message = "Description must be between 5 and 1000 characters")
    private String description;

    private Boolean complianceFlag;

    @Min(value = 1, message = "Sequence number must be at least 1")
    private Integer sequenceNumber;
}

