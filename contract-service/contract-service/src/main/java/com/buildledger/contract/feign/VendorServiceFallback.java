package com.buildledger.contract.feign;

import com.buildledger.contract.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component @Slf4j
public class VendorServiceFallback implements VendorServiceClient {
    public static final String MARKER = "SERVICE_UNAVAILABLE";
    @Override
    public ApiResponseDTO<Map<String, Object>> getVendorById(Long vendorId) {
        log.error("Vendor Service unavailable for vendorId={}", vendorId);
        return ApiResponseDTO.<Map<String, Object>>builder().success(false)
            .message(MARKER).build();
    }
}
