package com.smartshop.assistant;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AssistantServiceApplication {

    public static void main(String[] args) {
        // The orders database does not accept the legacy name "Asia/Calcutta".
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(AssistantServiceApplication.class, args);
    }
}