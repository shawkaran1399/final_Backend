package com.buildledger.iam.dto.request;

import com.buildledger.iam.enums.UserStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateUserRequestDTO {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s'\\-]+$", message = "Name must contain only letters, spaces, hyphens, or apostrophes")
    private String name;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Pattern(regexp = "^(\\+?[0-9\\s\\-().]{7,20})?$", message = "Phone number must be a valid national or international number")
    private String phone;

    private UserStatus status;
}

