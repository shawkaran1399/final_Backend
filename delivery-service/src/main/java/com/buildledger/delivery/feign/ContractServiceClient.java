package com.buildledger.delivery.feign;

import com.buildledger.delivery.dto.response.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(name = "contract-service", fallback = ContractServiceFallback.class)
public interface ContractServiceClient {

    @GetMapping("/api/contracts/{contractId}")
    ApiResponseDTO<Map<String, Object>> getContractById(@PathVariable("contractId") Long contractId);
}

