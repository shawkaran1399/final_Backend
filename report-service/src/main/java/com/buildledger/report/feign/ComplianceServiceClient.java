package com.buildledger.report.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@FeignClient(name = "compliance-service")
public interface ComplianceServiceClient {
    @GetMapping("/api/compliance")
    Map<String, Object> getAllCompliance();

    @GetMapping("/api/audits")
    Map<String, Object> getAllAudits();
}
