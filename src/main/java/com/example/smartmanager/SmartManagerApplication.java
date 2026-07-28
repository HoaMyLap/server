package com.example.smartmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SmartManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartManagerApplication.class, args);
    }
}
