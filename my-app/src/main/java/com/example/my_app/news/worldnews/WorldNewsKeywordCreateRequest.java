package com.example.my_app.news.worldnews;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorldNewsKeywordCreateRequest(
    String keyword,
    WorldNewsKeywordOperator operator,
    @JsonProperty("sort_order") Integer sortOrder) {}
