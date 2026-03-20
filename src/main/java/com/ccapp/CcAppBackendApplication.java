package com.ccapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CcAppBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CcAppBackendApplication.class, args);
    }
}
