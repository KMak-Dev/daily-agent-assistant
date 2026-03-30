package com.example.my_app.news.worldnews;

import com.example.my_app.news.NewsItem;
import com.example.my_app.news.NewsItemRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorldNewsIngestService {

  private static final Logger log = LoggerFactory.getLogger(WorldNewsIngestService.class);

  private static final DateTimeFormatter PUBLISH_DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final WorldNewsClient worldNewsClient;
  private final WorldNewsProperties properties;
  private final NewsItemRepository newsItemRepository;

  public WorldNewsIngestService(
      WorldNewsClient worldNewsClient,
      WorldNewsProperties properties,
      NewsItemRepository newsItemRepository) {
    this.worldNewsClient = worldNewsClient;
    this.properties = properties;
    this.newsItemRepository = newsItemRepository;
  }

  /**
   * Fetches one page from World News API and inserts new articles (deduped by {@code url}). Returns
   * the number of new rows saved.
   */
  public int ingestOnce() {
    if (!properties.ingestEnabled()) {
      return 0;
    }
    if (!properties.hasApiKey()) {
      log.debug("World News ingest skipped: no API key configured");
      return 0;
    }
    int size = Math.max(1, Math.min(properties.pageSize(), 100));
    Optional<SearchNewsResponse> response = worldNewsClient.searchNews(0, size);
    if (response.isEmpty()) {
      return 0;
    }
    List<SearchNewsResponse.WorldNewsArticle> articles = response.get().news();
    if (articles == null || articles.isEmpty()) {
      return 0;
    }
    int saved = 0;
    for (SearchNewsResponse.WorldNewsArticle article : articles) {
      if (article.url() == null || article.url().isBlank()) {
        continue;
      }
      if (newsItemRepository.existsByUrl(article.url())) {
        continue;
      }
      Optional<LocalDate> published = parsePublishDate(article.publishDate());
      if (published.isEmpty()) {
        log.debug("Skipping article with missing or invalid publish_date: {}", article.url());
        continue;
      }
      NewsItem item = new NewsItem();
      item.setUrl(article.url().trim());
      item.setTitle(normalizeTextForStorage(article.title()));
      item.setAuthors(
          article.authors() != null && !article.authors().isEmpty()
              ? article.authors().stream()
                  .map(WorldNewsIngestService::normalizeTextForStorage)
                  .filter(s -> !s.isEmpty())
                  .toList()
              : List.of());
      String content = article.text();
      if (content == null || content.isBlank()) {
        content = article.summary();
      }
      item.setContent(normalizeTextForStorage(content));
      item.setPublishedDate(published.get());
      newsItemRepository.save(item);
      saved++;
    }
    if (saved > 0) {
      log.info("World News ingest saved {} new article(s)", saved);
    }
    return saved;
  }

  /**
   * Replaces Unicode line breaks with a space, trims, and collapses runs of spaces so stored news
   * text is a single line.
   */
  static String normalizeTextForStorage(String s) {
    if (s == null) {
      return "";
    }
    String flattened = s.replaceAll("\\R+", " ");
    return flattened.trim().replaceAll(" +", " ");
  }

  private static Optional<LocalDate> parsePublishDate(String publishDate) {
    if (publishDate == null || publishDate.isBlank()) {
      return Optional.empty();
    }
    String datePart = publishDate.length() >= 10 ? publishDate.substring(0, 10) : publishDate;
    try {
      return Optional.of(LocalDate.parse(datePart));
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return Optional.of(LocalDate.parse(publishDate, PUBLISH_DATE));
    } catch (DateTimeParseException ignored) {
      return Optional.empty();
    }
  }
}
