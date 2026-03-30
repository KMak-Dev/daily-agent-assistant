package com.example.my_app.news.worldnews;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class WorldNewsSearchTextRefresher {

  private final WorldNewsKeywordRepository keywordRepository;
  private final WorldNewsSearchTextHolder searchTextHolder;

  public WorldNewsSearchTextRefresher(
      WorldNewsKeywordRepository keywordRepository, WorldNewsSearchTextHolder searchTextHolder) {
    this.keywordRepository = keywordRepository;
    this.searchTextHolder = searchTextHolder;
  }

  /** Reloads all keywords from Mongo and updates {@link WorldNewsSearchTextHolder}. */
  public void refresh() {
    List<WorldNewsKeyword> all =
        keywordRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder", "id"));
    if (all.isEmpty()) {
      searchTextHolder.setFromStartup(false, null);
      return;
    }
    searchTextHolder.setFromStartup(true, WorldNewsSearchTextBuilder.build(all));
  }
}
