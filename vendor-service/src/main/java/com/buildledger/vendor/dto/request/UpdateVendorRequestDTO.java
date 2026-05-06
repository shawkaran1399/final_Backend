package com.buildledger.vendor.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateVendorRequestDTO {

    @Size(min = 2, max = 100, message = "Name must be 2–100 characters")
    private String name;

    @Size(max = 200)
    private String contactInfo;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone must be a valid 10-digit Indian mobile number")
    private String phone;

    @Size(max = 100)
    private String category;

    @Size(max = 300)
    private String address;
}

