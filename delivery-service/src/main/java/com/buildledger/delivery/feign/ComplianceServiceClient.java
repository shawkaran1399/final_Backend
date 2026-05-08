package com.buildledger.delivery.feign;

import com.buildledger.delivery.dto.response.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "compliance-service", fallback = ComplianceServiceFallback.class)
public interface ComplianceServiceClient {

    @GetMapping("/api/compliance/check/{referenceId}")
    ApiResponseDTO<Boolean> isCompliancePassed(
            @PathVariable("referenceId") Long referenceId,
            @RequestParam("type") String type);
}
