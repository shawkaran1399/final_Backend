package com.buildledger.delivery.feign;

import com.buildledger.delivery.dto.response.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(name = "vendor-service", fallback = VendorServiceFallback.class)
public interface VendorServiceClient {
    @GetMapping("/api/vendors/{id}")
    ApiResponseDTO<Map<String, Object>> getVendorById(@PathVariable("id") Long id);
}