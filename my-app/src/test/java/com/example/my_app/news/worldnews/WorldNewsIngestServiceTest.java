package com.example.my_app.news.worldnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.my_app.news.NewsItem;
import com.example.my_app.news.NewsItemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorldNewsIngestServiceTest {

  @Mock WorldNewsClient worldNewsClient;
  @Mock WorldNewsProperties properties;
  @Mock NewsItemRepository newsItemRepository;

  @InjectMocks WorldNewsIngestService ingestService;

  @Test
  void ingestOnce_savesOnlyArticlesWithValidPublishDate() {
    when(properties.ingestEnabled()).thenReturn(true);
    when(properties.hasApiKey()).thenReturn(true);
    when(properties.pageSize()).thenReturn(10);
    when(worldNewsClient.searchNews(0, 10))
        .thenReturn(
            Optional.of(
                new SearchNewsResponse(
                    0,
                    2,
                    2,
                    List.of(
                        new SearchNewsResponse.WorldNewsArticle(
                            1L,
                            "Title",
                            "Full text",
                            null,
                            "https://example.com/a",
                            "2024-04-06 12:00:00",
                            List.of("Author One")),
                        new SearchNewsResponse.WorldNewsArticle(
                            2L,
                            "Other",
                            "x",
                            null,
                            "https://example.com/b",
                            "not-a-date",
                            List.of())))));
    when(newsItemRepository.existsByUrl("https://example.com/a")).thenReturn(false);
    when(newsItemRepository.existsByUrl("https://example.com/b")).thenReturn(false);

    int saved = ingestService.ingestOnce();

    assertThat(saved).isEqualTo(1);
    verify(newsItemRepository).save(any(NewsItem.class));
  }

  @Test
  void normalizeTextForStorage_replacesLineBreaksAndCollapsesSpaces() {
    assertThat(WorldNewsIngestService.normalizeTextForStorage("a\nb\rc")).isEqualTo("a b c");
    assertThat(WorldNewsIngestService.normalizeTextForStorage("x  \n  y")).isEqualTo("x y");
    assertThat(WorldNewsIngestService.normalizeTextForStorage(null)).isEmpty();
  }

  @Test
  void ingestOnce_stripsLineBreaksInSavedItem() {
    when(properties.ingestEnabled()).thenReturn(true);
    when(properties.hasApiKey()).thenReturn(true);
    when(properties.pageSize()).thenReturn(10);
    when(worldNewsClient.searchNews(0, 10))
        .thenReturn(
            Optional.of(
                new SearchNewsResponse(
                    0,
                    1,
                    1,
                    List.of(
                        new SearchNewsResponse.WorldNewsArticle(
                            1L,
                            "Line\nbroken\rtitle",
                            "First\r\n\r\nparagraph",
                            null,
                            "https://example.com/nl",
                            "2024-04-06 00:00:00",
                            List.of("Author\nName"))))));
    when(newsItemRepository.existsByUrl("https://example.com/nl")).thenReturn(false);

    ingestService.ingestOnce();

    ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
    verify(newsItemRepository).save(captor.capture());
    NewsItem saved = captor.getValue();
    assertThat(saved.getTitle()).isEqualTo("Line broken title");
    assertThat(saved.getContent()).isEqualTo("First paragraph");
    assertThat(saved.getAuthors()).containsExactly("Author Name");
  }

  @Test
  void ingestOnce_skipsWhenDuplicateUrl() {
    when(properties.ingestEnabled()).thenReturn(true);
    when(properties.hasApiKey()).thenReturn(true);
    when(properties.pageSize()).thenReturn(10);
    when(worldNewsClient.searchNews(0, 10))
        .thenReturn(
            Optional.of(
                new SearchNewsResponse(
                    0,
                    1,
                    1,
                    List.of(
                        new SearchNewsResponse.WorldNewsArticle(
                            1L,
                            "T",
                            "c",
                            null,
                            "https://example.com/x",
                            "2024-04-06 00:00:00",
                            List.of())))));
    when(newsItemRepository.existsByUrl("https://example.com/x")).thenReturn(true);

    int saved = ingestService.ingestOnce();

    assertThat(saved).isEqualTo(0);
    verify(newsItemRepository, never()).save(any());
  }
}
