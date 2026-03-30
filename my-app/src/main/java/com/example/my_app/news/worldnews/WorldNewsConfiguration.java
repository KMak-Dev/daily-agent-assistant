package com.example.my_app.news.worldnews;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WorldNewsProperties.class)
public class WorldNewsConfiguration {

  @Bean
  RestClient worldNewsRestClient() {
    return RestClient.builder().baseUrl("https://api.worldnewsapi.com").build();
  }
}
