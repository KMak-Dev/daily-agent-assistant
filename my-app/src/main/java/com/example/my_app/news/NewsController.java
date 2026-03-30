package com.example.my_app.news;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/news")
public class NewsController {

  private final NewsItemRepository newsItemRepository;

  public NewsController(NewsItemRepository newsItemRepository) {
    this.newsItemRepository = newsItemRepository;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public NewsPageResponse list(
      @PageableDefault(size = 20, sort = "publishedDate", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<NewsItem> page = newsItemRepository.findAll(pageable);
    return NewsPageResponse.from(page);
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public NewsItem create(@RequestBody NewsCreateRequest request) {
    NewsItem item = new NewsItem();
    item.setUrl(request.url());
    item.setTitle(request.title());
    item.setAuthor(request.author());
    item.setContent(request.content());
    item.setPublishedDate(request.publishedDate());
    return newsItemRepository.save(item);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteById(@PathVariable String id) {
    if (!newsItemRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    newsItemRepository.deleteById(id);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteByMatchingParams(
      @RequestParam(required = false) String url,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) List<String> author,
      @RequestParam(required = false) String content,
      @RequestParam(name = "published_date", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate publishedDate) {
    List<String> authorsForMatch = normalizeAuthorList(author);
    boolean hasCriteria =
        (url != null && !url.isBlank())
            || (title != null && !title.isBlank())
            || !authorsForMatch.isEmpty()
            || (content != null && !content.isBlank())
            || publishedDate != null;
    if (!hasCriteria) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Provide at least one query parameter to match");
    }

    NewsItem probe = new NewsItem();
    if (url != null && !url.isBlank()) {
      probe.setUrl(url);
    }
    if (title != null && !title.isBlank()) {
      probe.setTitle(title);
    }
    if (!authorsForMatch.isEmpty()) {
      probe.setAuthor(authorsForMatch);
    }
    if (content != null && !content.isBlank()) {
      probe.setContent(content);
    }
    if (publishedDate != null) {
      probe.setPublishedDate(publishedDate);
    }

    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    List<NewsItem> matches = newsItemRepository.findAll(Example.of(probe, matcher));
    if (matches.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    newsItemRepository.deleteAll(matches);
  }

  private static List<String> normalizeAuthorList(List<String> author) {
    if (author == null || author.isEmpty()) {
      return List.of();
    }
    return author.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }
}
