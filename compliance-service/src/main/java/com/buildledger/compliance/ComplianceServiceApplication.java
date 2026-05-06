package com.buildledger.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//@EnableScheduling
@EnableJpaAuditing
@EnableFeignClients

public class ComplianceServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ComplianceServiceApplication.class, args); }
}

