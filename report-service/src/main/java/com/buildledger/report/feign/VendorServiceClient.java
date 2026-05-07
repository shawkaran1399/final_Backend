package com.buildledger.report.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@FeignClient(name = "vendor-service")
public interface VendorServiceClient {
    @GetMapping("/api/vendors")
    Map<String, Object> getAllVendors();

    @GetMapping("/api/vendors/documents/status/PENDING")
    Map<String, Object> getPendingDocuments();
}
