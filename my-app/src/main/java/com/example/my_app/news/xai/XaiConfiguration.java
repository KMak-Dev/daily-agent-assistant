package com.example.my_app.news.xai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(XaiProperties.class)
public class XaiConfiguration {

  @Bean
  @Qualifier("xaiRestClient")
  RestClient xaiRestClient(XaiProperties properties) {
    String base =
        properties.baseUrl() != null && !properties.baseUrl().isBlank()
            ? properties.baseUrl().trim()
            : "https://api.x.ai";
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return RestClient.builder().baseUrl(base).build();
  }
}
