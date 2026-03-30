package com.example.my_app.news.worldnews;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorldNewsKeywordResponse(
    String id,
    String keyword,
    WorldNewsKeywordOperator operator,
    @JsonProperty("sort_order") int sortOrder) {

  static WorldNewsKeywordResponse from(WorldNewsKeyword entity) {
    return new WorldNewsKeywordResponse(
        entity.getId(), entity.getKeyword(), entity.getOperator(), entity.getSortOrder());
  }
}
