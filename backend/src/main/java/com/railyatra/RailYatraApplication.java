package com.railyatra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RailYatraApplication {
    public static void main(String[] args) { SpringApplication.run(RailYatraApplication.class, args); }
}
