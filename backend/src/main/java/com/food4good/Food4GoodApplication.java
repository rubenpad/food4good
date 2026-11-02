package com.food4good;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Food4GoodApplication {

    public static void main(String[] args) {
        SpringApplication.run(Food4GoodApplication.class, args);
    }
}
