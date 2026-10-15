package com.assessment.packing.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.assessment.packing")
public class PackingWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(PackingWebApplication.class, args);
    }
}
