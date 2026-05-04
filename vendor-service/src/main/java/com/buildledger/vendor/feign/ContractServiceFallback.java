package com.buildledger.vendor.feign;

import com.buildledger.vendor.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ContractServiceFallback implements ContractServiceClient {

    public static final String MARKER = "CONTRACT_SERVICE_UNAVAILABLE";

    @Override
    public ApiResponseDTO<List<Map<String, Object>>> getContractsByVendor(Long vendorId) {
        log.error("Contract Service unavailable – cannot fetch contracts for vendorId={}", vendorId);
        return ApiResponseDTO.<List<Map<String, Object>>>builder()
            .success(false).message(MARKER).build();
    }

    @Override
    public ApiResponseDTO<Void> propagateVendorName(Long vendorId, String name) {
        log.error("Contract Service unavailable – vendor name propagation skipped for vendorId={}", vendorId);
        return ApiResponseDTO.<Void>builder().success(false).message(MARKER).build();
    }
}
