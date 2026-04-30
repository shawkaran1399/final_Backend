package com.buildledger.report.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@FeignClient(name = "contract-service", contextId = "projectServiceClient")
public interface ProjectServiceClient {
    @GetMapping("/api/projects")
    Map<String, Object> getAllProjects();
}

