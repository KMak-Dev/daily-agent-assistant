package com.example.my_app.news;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record NewsCreateRequest(
    String url,
    String title,
    List<String> author,
    String content,
    @JsonProperty("published_date") LocalDate publishedDate) {}
