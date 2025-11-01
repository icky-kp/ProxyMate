package com.proj.gmailrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GmailRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmailRunnerApplication.class, args);
    }

}
