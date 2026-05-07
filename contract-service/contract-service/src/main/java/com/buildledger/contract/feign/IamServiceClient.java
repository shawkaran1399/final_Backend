package com.buildledger.contract.feign;

import com.buildledger.contract.dto.response.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(name = "iam-service", fallback = IamServiceFallback.class)
public interface IamServiceClient {
    @GetMapping("/api/users/{userId}")
    ApiResponseDTO<Map<String, Object>> getUserById(@PathVariable("userId") Long userId);
}

