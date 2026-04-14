package com.example.my_app.news.analysis;

import com.example.my_app.news.NewsItem;
import com.example.my_app.news.NewsItemRepository;
import com.example.my_app.news.dailybriefing.BriefingArchiveSource;
import com.example.my_app.news.dailybriefing.NewsBriefingArchiveService;
import com.example.my_app.news.xai.XaiClientException;
import com.example.my_app.news.xai.XaiProperties;
import com.example.my_app.news.xai.XaiResponsesClient;
import com.example.my_app.positions.StockPosition;
import com.example.my_app.positions.StockPositionRepository;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class NewsAnalyzeService {

  private static final Logger log = LoggerFactory.getLogger(NewsAnalyzeService.class);
  private static final int LOG_RAW_SUMMARY_MAX_CHARS = 1500;

  private static final int MAX_INCLUSIVE_DAYS = 7;

  private final NewsItemRepository newsItemRepository;
  private final StockPositionRepository stockPositionRepository;
  private final XaiResponsesClient xaiResponsesClient;
  private final XaiProperties xaiProperties;
  private final NewsAnalyzePrompts prompts;
  private final JsonMapper jsonMapper;
  private final NewsBriefingArchiveService briefingArchiveService;

  public NewsAnalyzeService(
      NewsItemRepository newsItemRepository,
      StockPositionRepository stockPositionRepository,
      XaiResponsesClient xaiResponsesClient,
      XaiProperties xaiProperties,
      NewsAnalyzePrompts prompts,
      JsonMapper jsonMapper,
      NewsBriefingArchiveService briefingArchiveService) {
    this.newsItemRepository = newsItemRepository;
    this.stockPositionRepository = stockPositionRepository;
    this.xaiResponsesClient = xaiResponsesClient;
    this.xaiProperties = xaiProperties;
    this.prompts = prompts;
    this.jsonMapper = jsonMapper;
    this.briefingArchiveService = briefingArchiveService;
  }

  public NewsAnalyzeResponse analyze(NewsAnalyzeRequest request) {
    return analyze(request, null);
  }

  /**
   * @param archiveSource when non-null, upserts the archived briefing row for this response’s date
   *     window and zone (cron and API share the same unique key).
   */
  public NewsAnalyzeResponse analyze(
      NewsAnalyzeRequest request, BriefingArchiveSource archiveSource) {
    if (request.startDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate is required");
    }

    ZoneId zone;
    try {
      String tz =
          request.timeZone() == null || request.timeZone().isBlank()
              ? "UTC"
              : request.timeZone().trim();
      zone = ZoneId.of(tz);
    } catch (DateTimeException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid timeZone: " + request.timeZone());
    }

    LocalDate endExclusive =
        request.endDate() != null ? request.endDate() : LocalDate.now(zone).plusDays(1);
    LocalDate start = request.startDate();

    if (start.isAfter(endExclusive)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "startDate must be on or before endDate (endDate is exclusive)");
    }

    long spanDays = endExclusive.toEpochDay() - start.toEpochDay();
    if (spanDays > MAX_INCLUSIVE_DAYS) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Date range too long: max "
              + MAX_INCLUSIVE_DAYS
              + " calendar days (endDate exclusive: publishedDate >= startDate and < endDate)");
    }

    if (!xaiProperties.hasApiKey()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "xAI API key is not configured");
    }

    List<NewsItem> articles =
        newsItemRepository.findByPublishedDateBetweenOrderByPublishedDateDesc(start, endExclusive);

    int batchSize = request.batchSize() != null ? request.batchSize() : xaiProperties.batchSize();
    if (batchSize < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "batchSize must be at least 1");
    }

    boolean refresh =
        request.refreshSummaries() != null && Boolean.TRUE.equals(request.refreshSummaries());

    List<NewsItem> needSummary = new ArrayList<>();
    for (NewsItem item : articles) {
      if (refresh || item.getSummary() == null || item.getSummary().isBlank()) {
        needSummary.add(item);
      }
    }

    int filled = 0;
    for (List<NewsItem> batch : partition(needSummary, batchSize)) {
      String userPrompt = buildBatchSummarizeUserPrompt(batch);
      String raw;
      try {
        raw = xaiResponsesClient.createResponse(prompts.summarizerInstructions(), userPrompt);
      } catch (XaiClientException e) {
        log.warn("News analyze: summarization xAI call failed: {}", e.getMessage(), e);
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "xAI summarization failed: " + e.getMessage(), e);
      }
      Map<String, String> idToSummary = parseSummaryJson(raw);
      for (NewsItem item : batch) {
        String s = idToSummary.get(item.getId());
        if (s != null && !s.isBlank()) {
          item.setSummary(s.trim());
          filled++;
        }
      }
      newsItemRepository.saveAll(batch);
    }

    List<StockPosition> positions =
        stockPositionRepository.findAll(Sort.by(Sort.Direction.ASC, "symbol"));
    String task = resolveBriefingPrompt(request);
    String briefingInput =
        buildBriefingUserPrompt(start, endExclusive, zone.getId(), task, positions, articles);
    String briefing;
    try {
      briefing =
          xaiResponsesClient.createResponse(prompts.synthesizerInstructions(), briefingInput);
    } catch (XaiClientException e) {
      log.warn("News analyze: briefing xAI call failed: {}", e.getMessage(), e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "xAI briefing failed: " + e.getMessage(), e);
    }

    briefing = BriefingTextUtils.normalizeBriefingLineBreaks(briefing);

    NewsAnalyzeResponse response =
        new NewsAnalyzeResponse(
            start, endExclusive, zone.getId(), articles.size(), filled, briefing);
    if (archiveSource != null) {
      briefingArchiveService.upsert(response, archiveSource);
    }
    return response;
  }

  private String buildBatchSummarizeUserPrompt(List<NewsItem> batch) {
    int maxChars = Math.max(200, xaiProperties.excerptMaxChars());
    StringBuilder sb = new StringBuilder();
    sb.append(
        "For each article block below, write around a paragraph of text (150 words max)to summarize them "
            + "capturing the main event, dates, or relevant details for analyzing stock positions. Output ONLY a JSON array of objects with keys "
            + "\"id\" and \"summary\" (string) and \"date\" (string). Use the exact \"id\" values from the blocks. "
            + "No markdown fences, no commentary.\n\n");
    for (NewsItem item : batch) {
      String excerpt = excerpt(item.getContent(), maxChars);
      sb.append("---\nID: ")
          .append(item.getId())
          .append("\nDate: ")
          .append(item.getPublishedDate())
          .append("\nTitle: ")
          .append(item.getTitle() != null ? item.getTitle() : "")
          .append("\nExcerpt: ")
          .append(excerpt)
          .append("\n");
    }
    return sb.toString();
  }

  private static String excerpt(String content, int maxChars) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String t = content.trim();
    if (t.length() <= maxChars) {
      return t;
    }
    return t.substring(0, maxChars) + "…";
  }

  private String resolveBriefingPrompt(NewsAnalyzeRequest request) {
    if (request.briefingPrompt() != null && !request.briefingPrompt().isBlank()) {
      return request.briefingPrompt().trim();
    }
    if (xaiProperties.briefingPrompt() != null && !xaiProperties.briefingPrompt().isBlank()) {
      return xaiProperties.briefingPrompt().trim();
    }
    return prompts.defaultBriefingPrompt();
  }

  private String buildBriefingUserPrompt(
      LocalDate start,
      LocalDate endExclusive,
      String zoneId,
      String task,
      List<StockPosition> positions,
      List<NewsItem> articles) {
    StringBuilder sb = new StringBuilder();
    sb.append("## Task\n").append(task).append("\n\n");
    sb.append("## Date context\n")
        .append("Articles dated ")
        .append(start)
        .append(" <= publishedDate < ")
        .append(endExclusive)
        .append(". Reader zone: ")
        .append(zoneId)
        .append(".\n\n");
    sb.append("## Our current stock positions\n");
    if (positions == null || positions.isEmpty()) {
      sb.append("(none on file — do not assume we hold any symbols.)\n\n");
    } else {
      for (StockPosition p : positions) {
        sb.append("- ")
            .append(p.getSymbol() != null ? p.getSymbol() : "?")
            .append(": ")
            .append(
                p.getQuantity() != null
                    ? p.getQuantity().stripTrailingZeros().toPlainString()
                    : "?")
            .append(" shares\n");
      }
      sb.append("\n");
    }
    sb.append("## Summarized news\n");
    for (NewsItem item : articles) {
      String sum = item.getSummary();
      if (sum == null || sum.isBlank()) {
        sum = "(no summary)";
      }
      sb.append("- ")
          .append(item.getPublishedDate())
          .append(" | ")
          .append(item.getTitle() != null ? item.getTitle() : "")
          .append(" | ")
          .append(sum)
          .append("\n  ")
          .append(item.getUrl() != null ? item.getUrl() : "")
          .append("\n");
    }
    return sb.toString();
  }

  private Map<String, String> parseSummaryJson(String raw) {
    String text = stripMarkdownFence(raw);
    JsonNode root;
    try {
      root = jsonMapper.readTree(text);
    } catch (Exception e) {
      log.warn(
          "News analyze: summarizer output is not valid JSON: {} — raw prefix: {}",
          e.getMessage(),
          truncateForLog(raw));
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Summarizer did not return valid JSON: " + e.getMessage());
    }
    if (!root.isArray()) {
      log.warn(
          "News analyze: summarizer JSON is not an array — raw prefix: {}", truncateForLog(raw));
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Summarizer JSON must be a top-level array");
    }
    Map<String, String> map = new HashMap<>();
    for (JsonNode n : root) {
      String id = n.path("id").asText(null);
      String summary = n.path("summary").asText(null);
      if (id != null && summary != null && !summary.isBlank()) {
        map.put(id, summary);
      }
    }
    return map;
  }

  private static String truncateForLog(String s) {
    if (s == null) {
      return "(null)";
    }
    String t = s.replace("\r\n", "\n").trim();
    if (t.length() <= LOG_RAW_SUMMARY_MAX_CHARS) {
      return t;
    }
    return t.substring(0, LOG_RAW_SUMMARY_MAX_CHARS) + "…(truncated)";
  }

  static String stripMarkdownFence(String raw) {
    if (raw == null) {
      return "";
    }
    String t = raw.trim();
    if (t.startsWith("```")) {
      int firstNl = t.indexOf('\n');
      if (firstNl > 0) {
        t = t.substring(firstNl + 1);
      }
      int endFence = t.lastIndexOf("```");
      if (endFence >= 0) {
        t = t.substring(0, endFence);
      }
      return t.trim();
    }
    return t;
  }

  private static <T> List<List<T>> partition(List<T> list, int size) {
    List<List<T>> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      out.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return out;
  }
}
