package com.buildledger.contract.feign;

import com.buildledger.contract.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component @Slf4j
public class IamServiceFallback implements IamServiceClient {
    public static final String MARKER = "SERVICE_UNAVAILABLE";
    @Override
    public ApiResponseDTO<Map<String, Object>> getUserById(Long userId) {
        log.error("IAM Service unavailable for userId={}", userId);
        return ApiResponseDTO.<Map<String, Object>>builder().success(false)
            .message(MARKER).build();
    }
}
