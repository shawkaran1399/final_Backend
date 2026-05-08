package com.buildledger.delivery;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients
@EnableScheduling
public class DeliveryServiceApplication {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().directory("C:\\Users\\2478574\\Videos\\final_backend\\final_Backend\\.env").ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }
}

