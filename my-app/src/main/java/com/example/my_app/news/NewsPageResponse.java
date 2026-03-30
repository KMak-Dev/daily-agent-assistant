package com.example.my_app.news;

import java.util.List;
import org.springframework.data.domain.Page;

public record NewsPageResponse(
    List<NewsItem> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  public static NewsPageResponse from(Page<NewsItem> page) {
    return new NewsPageResponse(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
