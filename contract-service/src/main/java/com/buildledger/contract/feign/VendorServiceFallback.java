package com.buildledger.contract.feign;

import com.buildledger.contract.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fallback for VendorServiceClient — returns a safe default when vendor-service is unavailable.
 * MARKER string is checked in ContractServiceImpl to detect fallback responses.
 */
@Component
@Slf4j
public class VendorServiceFallback implements VendorServiceClient {

    public static final String MARKER = "SERVICE_UNAVAILABLE";

    @Override
    public ApiResponseDTO<Map<String, Object>> getVendorById(Long vendorId) {
        log.error("Vendor Service unavailable for vendorId={}", vendorId);
        return ApiResponseDTO.<Map<String, Object>>builder()
                .success(false)
                .message(MARKER)
                .build();
    }

    // ← NEW — required because VendorServiceClient now declares getVendorByUsername()
    @Override
    public ApiResponseDTO<Map<String, Object>> getVendorByUsername(String username) {
        log.error("Vendor Service unavailable for username={}", username);
        return ApiResponseDTO.<Map<String, Object>>builder()
                .success(false)
                .message(MARKER)
                .build();
    }
}