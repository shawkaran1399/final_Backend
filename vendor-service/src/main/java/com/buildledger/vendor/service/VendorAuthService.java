package com.buildledger.vendor.service;

import com.buildledger.vendor.dto.request.VendorLoginRequestDTO;
import com.buildledger.vendor.dto.response.VendorLoginResponseDTO;

public interface VendorAuthService {
    VendorLoginResponseDTO loginPendingVendor(VendorLoginRequestDTO request);
}

