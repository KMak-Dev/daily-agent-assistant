package com.example.my_app.news.dailybriefing;

import java.util.List;
import org.springframework.data.domain.Page;

public record NewsBriefingPageResponse(
    List<NewsDailyBriefing> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  public static NewsBriefingPageResponse from(Page<NewsDailyBriefing> page) {
    return new NewsBriefingPageResponse(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
