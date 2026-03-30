package com.example.my_app.news.worldnews;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "world_news_keywords")
public class WorldNewsKeyword {

  @Id private String id;

  /** Lower values first; defaults to 0 when unset in Mongo. */
  @Field("sort_order")
  private int sortOrder;

  private String keyword;
  private WorldNewsKeywordOperator operator;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  public WorldNewsKeywordOperator getOperator() {
    return operator;
  }

  public void setOperator(WorldNewsKeywordOperator operator) {
    this.operator = operator;
  }
}
