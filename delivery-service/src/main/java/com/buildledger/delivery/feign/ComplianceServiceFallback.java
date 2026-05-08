package com.buildledger.delivery.feign;

import com.buildledger.delivery.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ComplianceServiceFallback implements ComplianceServiceClient {

    public static final String MARKER = "COMPLIANCE_SERVICE_UNAVAILABLE";

    @Override
    public ApiResponseDTO<Boolean> isCompliancePassed(Long referenceId, String type) {
        log.error("Compliance Service is unavailable for referenceId={} type={}", referenceId, type);
        return ApiResponseDTO.<Boolean>builder()
                .success(false)
                .message(MARKER)
                .data(null)
                .build();
    }
}
