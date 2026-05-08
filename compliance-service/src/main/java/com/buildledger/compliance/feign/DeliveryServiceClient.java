package com.buildledger.compliance.feign;

import com.buildledger.compliance.dto.response.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(name = "delivery-service", fallback = DeliveryServiceFallback.class)
public interface DeliveryServiceClient {

    @GetMapping("/api/deliveries/{deliveryId}")
    ApiResponseDTO<Map<String, Object>> getDeliveryById(@PathVariable("deliveryId") Long deliveryId);

    @GetMapping("/api/services/{serviceId}")
    ApiResponseDTO<Map<String, Object>> getServiceById(@PathVariable("serviceId") Long serviceId);
}
