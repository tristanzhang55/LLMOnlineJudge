package com.tfzhang.linuxback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LinuxbackApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinuxbackApplication.class, args);
    }
}
