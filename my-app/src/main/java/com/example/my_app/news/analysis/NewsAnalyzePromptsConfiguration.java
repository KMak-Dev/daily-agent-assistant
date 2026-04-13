package com.example.my_app.news.analysis;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

@Configuration
public class NewsAnalyzePromptsConfiguration {

  private static final String LOCATION = "classpath:prompts/news-analyze.yaml";

  @Bean
  NewsAnalyzePrompts newsAnalyzePrompts(ResourceLoader resourceLoader) throws IOException {
    Resource resource = resourceLoader.getResource(LOCATION);
    return loadPrompts(resource);
  }

  static NewsAnalyzePrompts loadPrompts(Resource resource) throws IOException {
    if (!resource.exists()) {
      throw new IllegalStateException("Missing prompts resource: " + resource);
    }
    LoaderOptions loaderOptions = new LoaderOptions();
    Yaml yaml = new Yaml(new Constructor(NewsAnalyzePromptsYamlRoot.class, loaderOptions));
    NewsAnalyzePromptsYamlRoot root;
    try (InputStream in = resource.getInputStream()) {
      root = yaml.load(in);
    }
    if (root == null) {
      throw new IllegalStateException("Empty YAML: " + resource);
    }
    String s1 = requireText(root.summarizerInstructions, "summarizerInstructions");
    String s2 = requireText(root.synthesizerInstructions, "synthesizerInstructions");
    String s3 = requireText(root.defaultBriefingPrompt, "defaultBriefingPrompt");
    return new NewsAnalyzePrompts(s1, s2, s3);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing or blank prompt field: " + name);
    }
    return value.trim();
  }
}
