package com.nexus.ingress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Application Entry Point for Zero-Trust Enforcement Point (ZTEP) Ingress Shield.
 */
@SpringBootApplication
public class IngressShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngressShieldApplication.class, args);
    }
}
