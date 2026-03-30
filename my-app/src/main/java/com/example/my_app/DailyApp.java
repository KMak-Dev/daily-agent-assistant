package com.example.my_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DailyApp {

  public static void main(String[] args) {
    SpringApplication.run(DailyApp.class, args);
  }
}
