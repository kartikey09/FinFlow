package io.finflow.chaosapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Chaos API — a single Spring Boot application that pretends to be both
 * AWS and GCP at once. It serves synthetic billing data and accepts fake
 * commitment-purchase requests, and (from Day 4) injects faults on demand.
 *
 * This is a TEST DOUBLE, not a real integration. See README.md.
 */

@SpringBootApplication
public class ChaosApiApplication {
    public static void main(String[] args){
        SpringApplication.run(ChaosApiApplication.class, args);
    }
}