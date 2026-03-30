package com.example.my_app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PaginationConfig {

  @Bean
  PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
    return resolver -> resolver.setMaxPageSize(100);
  }
}
