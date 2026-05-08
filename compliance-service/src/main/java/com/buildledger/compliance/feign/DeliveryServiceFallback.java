package com.buildledger.compliance.feign;

import com.buildledger.compliance.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Slf4j
public class DeliveryServiceFallback implements DeliveryServiceClient {

    public static final String MARKER = "DELIVERY_SERVICE_UNAVAILABLE";

    @Override
    public ApiResponseDTO<Map<String, Object>> getDeliveryById(Long deliveryId) {
        log.error("Delivery Service unavailable for deliveryId={}", deliveryId);
        return ApiResponseDTO.<Map<String, Object>>builder().success(false).message(MARKER).build();
    }

    @Override
    public ApiResponseDTO<Map<String, Object>> getServiceById(Long serviceId) {
        log.error("Delivery Service unavailable for serviceId={}", serviceId);
        return ApiResponseDTO.<Map<String, Object>>builder().success(false).message(MARKER).build();
    }
}
