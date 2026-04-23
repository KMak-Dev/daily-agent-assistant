package com.example.my_app.news.retention;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NewsItemRetentionProperties.class)
public class NewsItemRetentionConfiguration {}
