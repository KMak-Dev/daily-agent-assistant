package com.example.my_app.news.worldnews;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class WorldNewsSearchTextLoader implements ApplicationRunner {

  private final WorldNewsSearchTextRefresher searchTextRefresher;

  public WorldNewsSearchTextLoader(WorldNewsSearchTextRefresher searchTextRefresher) {
    this.searchTextRefresher = searchTextRefresher;
  }

  @Override
  public void run(ApplicationArguments args) {
    searchTextRefresher.refresh();
  }
}
