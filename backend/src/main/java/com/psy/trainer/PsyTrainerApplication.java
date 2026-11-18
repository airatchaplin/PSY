package com.psy.trainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PsyTrainerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PsyTrainerApplication.class, args);
    }
}
