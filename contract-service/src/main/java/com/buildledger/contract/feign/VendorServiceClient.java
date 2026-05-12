package com.buildledger.contract.feign;

import com.buildledger.contract.dto.response.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign client for vendor-service.
 * Used by ContractServiceImpl to:
 *   1. Validate vendor is ACTIVE when creating contracts
 *   2. Look up vendorId by username for GET /contracts/vendor/my
 */
@FeignClient(
        name     = "vendor-service",
        fallback = VendorServiceFallback.class
)
public interface VendorServiceClient {

    // Get vendor by ID — used for contract creation validation
    @GetMapping("/api/vendors/{vendorId}")
    ApiResponseDTO<Map<String, Object>> getVendorById(@PathVariable Long vendorId);

    // Get vendor by username — used by getContractsByVendorUsername()
    // Returns vendor object including vendorId so we can find their contracts
    @GetMapping("/api/vendors/username/{username}")
    ApiResponseDTO<Map<String, Object>> getVendorByUsername(@PathVariable String username);
}