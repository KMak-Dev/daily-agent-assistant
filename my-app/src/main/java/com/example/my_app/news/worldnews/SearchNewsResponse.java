package com.example.my_app.news.worldnews;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchNewsResponse(
    int offset, int number, int available, List<WorldNewsArticle> news) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record WorldNewsArticle(
      long id,
      String title,
      String text,
      String summary,
      String url,
      @JsonProperty("publish_date") String publishDate,
      List<String> authors) {}
}
