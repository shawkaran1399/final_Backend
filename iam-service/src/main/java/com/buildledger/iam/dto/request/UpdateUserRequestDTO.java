package com.buildledger.iam.dto.request;

import com.buildledger.iam.enums.UserStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateUserRequestDTO {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Name must contain only alphabets")
    private String name;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone number must be a valid 10-digit Indian number")
    private String phone;

    private UserStatus status;
}

