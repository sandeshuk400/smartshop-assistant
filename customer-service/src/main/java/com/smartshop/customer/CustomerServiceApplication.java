package com.smartshop.customer;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomerServiceApplication {

    public static void main(String[] args) {
        // Postgres rejects the legacy name "Asia/Calcutta"; use the current "Asia/Kolkata".
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}