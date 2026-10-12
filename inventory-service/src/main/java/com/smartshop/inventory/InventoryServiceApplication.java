package com.smartshop.inventory;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        // Postgres rejects the legacy name "Asia/Calcutta"; use the current "Asia/Kolkata".
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}