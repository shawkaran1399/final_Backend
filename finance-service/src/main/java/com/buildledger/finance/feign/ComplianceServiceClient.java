package com.buildledger.finance.feign;

import com.buildledger.finance.dto.response.ApiResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// Disabled — INVOICE_CHECK compliance gate removed; retained for compilation safety
public interface ComplianceServiceClient {

    @GetMapping("/api/compliance/check/{referenceId}")
    ApiResponseDTO<Boolean> isCompliancePassed(
            @PathVariable("referenceId") Long referenceId,
            @RequestParam("type") String type);
}
