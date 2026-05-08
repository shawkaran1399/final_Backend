package com.buildledger.iam;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class IamServiceApplication {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().directory("C:\\Users\\2478574\\Videos\\final_backend\\final_Backend\\.env").ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));

// Add this temporarily:
        System.out.println("JWT_SECRET loaded: " + System.getProperty("JWT_SECRET"));
        SpringApplication.run(IamServiceApplication.class, args);
    }
}

