package com.example.my_app.news.analysis;

import com.example.my_app.news.dailybriefing.BriefingArchiveSource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsAnalyzeController {

  private final NewsAnalyzeService newsAnalyzeService;

  public NewsAnalyzeController(NewsAnalyzeService newsAnalyzeService) {
    this.newsAnalyzeService = newsAnalyzeService;
  }

  @PostMapping(
      value = "/analyze",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public NewsAnalyzeResponse analyze(@RequestBody NewsAnalyzeRequest request) {
    return newsAnalyzeService.analyze(request, BriefingArchiveSource.API);
  }
}
