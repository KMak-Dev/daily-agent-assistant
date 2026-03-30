package com.example.my_app.news.worldnews;

import java.util.List;

final class WorldNewsSearchTextBuilder {

  private WorldNewsSearchTextBuilder() {}

  /**
   * Rebuilds search-news {@code text} from stored rows in order: each {@code OR} term is joined
   * with {@code " OR "}; each {@code NOT} term is appended as {@code -keyword} (with a leading
   * space when not at the start).
   */
  static String build(List<WorldNewsKeyword> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (WorldNewsKeyword row : keywords) {
      if (row == null || row.getOperator() == null) {
        continue;
      }
      String term = row.getKeyword() == null ? "" : row.getKeyword().trim();
      if (term.isEmpty()) {
        continue;
      }
      if (row.getOperator() == WorldNewsKeywordOperator.NOT) {
        if (sb.isEmpty()) {
          sb.append('-').append(term);
        } else {
          sb.append(" -").append(term);
        }
      } else {
        if (sb.isEmpty()) {
          sb.append(term);
        } else {
          sb.append(" OR ").append(term);
        }
      }
    }
    return sb.toString();
  }
}
