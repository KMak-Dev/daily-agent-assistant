package com.example.my_app;

import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

  @GetMapping("/")
  public Callable<Map<String, String>> status() {
    return () -> Map.of("status", "running");
  }
}
