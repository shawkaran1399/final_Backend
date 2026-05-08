package com.buildledger.delivery.feign;

import com.buildledger.delivery.dto.response.ApiResponseDTO;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class VendorServiceFallback implements VendorServiceClient {

    public static final String MARKER = "VENDOR_SERVICE_FALLBACK";

    @Override
    public ApiResponseDTO<Map<String, Object>> getVendorById(Long id) {
        return ApiResponseDTO.<Map<String, Object>>builder()
                .success(false)
                .message(MARKER)
                .build();
    }
}