package com.buildledger.finance.feign;

import com.buildledger.finance.dto.response.ApiResponseDTO;

// Disabled — INVOICE_CHECK compliance gate removed; retained for compilation safety
public class ComplianceServiceFallback implements ComplianceServiceClient {

    public static final String MARKER = "COMPLIANCE_SERVICE_UNAVAILABLE";

    @Override
    public ApiResponseDTO<Boolean> isCompliancePassed(Long referenceId, String type) {
        return ApiResponseDTO.<Boolean>builder().success(false).message(MARKER).data(null).build();
    }
}
