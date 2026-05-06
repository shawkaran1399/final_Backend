package com.buildledger.vendor.dto.response;

import com.buildledger.vendor.enums.VendorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorLoginResponseDTO {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private Long vendorId;
    private String username;
    private String name;
    private String email;
    private VendorStatus status;
}

