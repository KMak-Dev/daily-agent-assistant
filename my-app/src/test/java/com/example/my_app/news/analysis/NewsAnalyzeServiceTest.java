package com.example.my_app.news.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.my_app.news.NewsItem;
import com.example.my_app.news.NewsItemRepository;
import com.example.my_app.news.dailybriefing.BriefingArchiveSource;
import com.example.my_app.news.dailybriefing.NewsBriefingArchiveService;
import com.example.my_app.news.xai.XaiProperties;
import com.example.my_app.news.xai.XaiResponsesClient;
import com.example.my_app.positions.StockPosition;
import com.example.my_app.positions.StockPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NewsAnalyzeServiceTest {

  @Mock private NewsItemRepository newsItemRepository;
  @Mock private StockPositionRepository stockPositionRepository;
  @Mock private XaiResponsesClient xaiResponsesClient;
  @Mock private NewsBriefingArchiveService briefingArchiveService;

  @Captor private ArgumentCaptor<List<NewsItem>> savedItemsCaptor;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private NewsAnalyzeService service;

  @BeforeEach
  void setUp() throws Exception {
    NewsAnalyzePrompts prompts =
        NewsAnalyzePromptsConfiguration.loadPrompts(
            new ClassPathResource("prompts/news-analyze.yaml"));
    XaiProperties props = new XaiProperties("secret", "grok-test", "https://api.x.ai", 10, 500, "");
    service =
        new NewsAnalyzeService(
            newsItemRepository,
            stockPositionRepository,
            xaiResponsesClient,
            props,
            prompts,
            jsonMapper,
            briefingArchiveService);
  }

  @Test
  void analyze_rejectsStartAfterEnd() {
    NewsAnalyzeRequest req =
        new NewsAnalyzeRequest(
            LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 1), "UTC", null, null, null);
    assertThatThrownBy(() -> service.analyze(req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("startDate");
  }

  @Test
  void analyze_rejectsMoreThanSevenDaySpanExclusiveEnd() {
    NewsAnalyzeRequest req =
        new NewsAnalyzeRequest(
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 9), "UTC", null, null, null);
    assertThatThrownBy(() -> service.analyze(req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("max");
  }

  @Test
  void analyze_rejectsMissingApiKey() throws Exception {
    NewsAnalyzePrompts prompts =
        NewsAnalyzePromptsConfiguration.loadPrompts(
            new ClassPathResource("prompts/news-analyze.yaml"));
    XaiProperties emptyKey = new XaiProperties("", "m", "https://api.x.ai", 10, 500, "");
    NewsAnalyzeService noKey =
        new NewsAnalyzeService(
            newsItemRepository,
            stockPositionRepository,
            xaiResponsesClient,
            emptyKey,
            prompts,
            jsonMapper,
            briefingArchiveService);
    NewsAnalyzeRequest req =
        new NewsAnalyzeRequest(
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), "UTC", null, null, null);
    assertThatThrownBy(() -> noKey.analyze(req))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("API key");
  }

  @Test
  void analyze_batchesSummariesAndBriefing() {
    LocalDate d = LocalDate.of(2026, 4, 1);
    NewsItem a = new NewsItem();
    a.setId("id-a");
    a.setUrl("https://example.com/a");
    a.setTitle("Title A");
    a.setContent("Long body ".repeat(20));
    a.setPublishedDate(d);
    a.setSummary(null);

    LocalDate endExclusive = d.plusDays(1);
    when(newsItemRepository.findByPublishedDateBetweenOrderByPublishedDateDesc(d, endExclusive))
        .thenReturn(List.of(a));
    when(stockPositionRepository.findAll(any(Sort.class))).thenReturn(List.of());
    when(xaiResponsesClient.createResponse(anyString(), anyString()))
        .thenReturn(
            "[{\"id\":\"id-a\",\"summary\":\"One line about A.\"}]",
            "Weekly briefing text.\r\n\r\n\r\n");

    NewsAnalyzeResponse res =
        service.analyze(new NewsAnalyzeRequest(d, endExclusive, "America/New_York", 5, null, null));

    assertThat(res.articleCount()).isEqualTo(1);
    assertThat(res.summariesFilledThisRun()).isEqualTo(1);
    assertThat(res.briefing()).isEqualTo("Weekly briefing text.");
    assertThat(res.timeZone()).isEqualTo("America/New_York");

    verify(newsItemRepository).saveAll(savedItemsCaptor.capture());
    assertThat(savedItemsCaptor.getValue().getFirst().getSummary()).isEqualTo("One line about A.");
    verify(briefingArchiveService, times(0)).upsert(any(), any());
  }

  @Test
  void analyze_upsertsArchiveWhenSourceProvided() {
    LocalDate d = LocalDate.of(2026, 4, 1);
    NewsItem a = new NewsItem();
    a.setId("id-a");
    a.setUrl("https://example.com/a");
    a.setTitle("Title A");
    a.setContent("body");
    a.setPublishedDate(d);
    a.setSummary("already");

    LocalDate endExclusive = d.plusDays(1);
    when(newsItemRepository.findByPublishedDateBetweenOrderByPublishedDateDesc(d, endExclusive))
        .thenReturn(List.of(a));
    when(stockPositionRepository.findAll(any(Sort.class))).thenReturn(List.of());
    when(xaiResponsesClient.createResponse(anyString(), anyString())).thenReturn("Briefing out.");

    service.analyze(
        new NewsAnalyzeRequest(d, endExclusive, "UTC", 5, false, null),
        BriefingArchiveSource.API);

    verify(briefingArchiveService, times(1))
        .upsert(any(NewsAnalyzeResponse.class), eq(BriefingArchiveSource.API));
  }

  @Test
  void analyze_includesStockPositionsInSynthesisInput() {
    LocalDate d = LocalDate.of(2026, 4, 1);
    NewsItem a = new NewsItem();
    a.setId("id-a");
    a.setUrl("https://example.com/a");
    a.setTitle("Title A");
    a.setContent("body");
    a.setPublishedDate(d);
    a.setSummary("Summary line.");

    StockPosition pos = new StockPosition();
    pos.setSymbol("AAPL");
    pos.setQuantity(new BigDecimal("10.5"));

    LocalDate endExclusive = d.plusDays(1);
    when(newsItemRepository.findByPublishedDateBetweenOrderByPublishedDateDesc(d, endExclusive))
        .thenReturn(List.of(a));
    when(stockPositionRepository.findAll(any(Sort.class))).thenReturn(List.of(pos));
    when(xaiResponsesClient.createResponse(anyString(), anyString())).thenReturn("ok");

    service.analyze(
        new NewsAnalyzeRequest(d, endExclusive, "UTC", 5, false, "Custom task line for briefing."));

    ArgumentCaptor<String> inputCaptor = ArgumentCaptor.forClass(String.class);
    verify(xaiResponsesClient, times(1)).createResponse(anyString(), inputCaptor.capture());
    String synthesisInput = inputCaptor.getValue();
    assertThat(synthesisInput).contains("Custom task line for briefing.");
    assertThat(synthesisInput)
        .contains("Our current stock positions")
        .contains("AAPL: 10.5 shares");
    assertThat(synthesisInput).contains("Summarized news");
  }

  @Test
  void stripMarkdownFence_stripsJsonFence() {
    String raw =
        """
        ```json
        [{"id":"x","summary":"y"}]
        ```
        """;
    assertThat(NewsAnalyzeService.stripMarkdownFence(raw).trim())
        .isEqualTo("[{\"id\":\"x\",\"summary\":\"y\"}]");
  }
}
