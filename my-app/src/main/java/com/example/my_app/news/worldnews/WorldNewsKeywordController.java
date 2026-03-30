package com.example.my_app.news.worldnews;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/world-news/keywords")
public class WorldNewsKeywordController {

  private final WorldNewsKeywordRepository keywordRepository;
  private final WorldNewsSearchTextRefresher searchTextRefresher;

  public WorldNewsKeywordController(
      WorldNewsKeywordRepository keywordRepository,
      WorldNewsSearchTextRefresher searchTextRefresher) {
    this.keywordRepository = keywordRepository;
    this.searchTextRefresher = searchTextRefresher;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public List<WorldNewsKeywordResponse> list() {
    return keywordRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder", "id")).stream()
        .map(WorldNewsKeywordResponse::from)
        .toList();
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public WorldNewsKeywordResponse create(@RequestBody WorldNewsKeywordCreateRequest request) {
    if (request.keyword() == null || request.keyword().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'keyword' is required");
    }
    if (request.operator() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'operator' is required");
    }
    WorldNewsKeyword entity = new WorldNewsKeyword();
    entity.setKeyword(request.keyword().trim());
    entity.setOperator(request.operator());
    entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
    WorldNewsKeyword saved = keywordRepository.save(entity);
    searchTextRefresher.refresh();
    return WorldNewsKeywordResponse.from(saved);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteById(@PathVariable String id) {
    if (!keywordRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    keywordRepository.deleteById(id);
    searchTextRefresher.refresh();
  }
}
