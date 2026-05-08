package com.buildledger.report.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@FeignClient(name = "delivery-service")
public interface DeliveryServiceClient {
    @GetMapping("/api/deliveries")
    Map<String, Object> getAllDeliveries();

    @GetMapping("/api/services")
    Map<String, Object> getAllServices();
}

