package com.buildledger.report.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@FeignClient(name = "contract-service", contextId = "contractServiceClient")
public interface ContractServiceClient {
    @GetMapping("/api/contracts")
    Map<String, Object> getAllContracts();
}

