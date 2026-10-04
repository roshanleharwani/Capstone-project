package com.nexusgateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.nexus.ingress", 
    "com.nexus.compression", 
    "com.nexus.market", 
    "com.nexus.router",
    "com.nexusgateway.app"
})
public class NexusGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusGatewayApplication.class, args);
    }
}
