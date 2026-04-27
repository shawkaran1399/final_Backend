package com.buildledger.vendor.dto.response;

import com.buildledger.vendor.enums.VendorStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VendorResponseDTO {
    private Long vendorId;
    private String name;
    private String contactInfo;
    private String email;
    private String phone;
    private String category;
    private String address;
    private String username;
    private VendorStatus status;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

