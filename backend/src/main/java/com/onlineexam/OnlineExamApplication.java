package com.onlineexam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class OnlineExamApplication {
  public static void main(String[] args) {
    SpringApplication.run(OnlineExamApplication.class, args);
  }
}
