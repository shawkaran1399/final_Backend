package com.buildledger.delivery.feign;

import com.buildledger.delivery.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Slf4j
public class ContractServiceFallback implements ContractServiceClient {

    public static final String MARKER = "SERVICE_UNAVAILABLE";

    @Override
    public ApiResponseDTO<Map<String, Object>> getContractById(Long contractId) {
        log.error("Contract Service is unavailable for contractId={}", contractId);
        return ApiResponseDTO.<Map<String, Object>>builder()
            .success(false)
            .message(MARKER)
            .build();
    }
}

