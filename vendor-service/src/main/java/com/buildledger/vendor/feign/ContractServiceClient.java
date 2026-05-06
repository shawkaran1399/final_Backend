package com.buildledger.vendor.feign;

import com.buildledger.vendor.dto.response.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "contract-service", fallback = ContractServiceFallback.class)
public interface ContractServiceClient {

    @GetMapping("/api/contracts/vendor/{vendorId}")
    ApiResponseDTO<List<Map<String, Object>>> getContractsByVendor(@PathVariable("vendorId") Long vendorId);
}
