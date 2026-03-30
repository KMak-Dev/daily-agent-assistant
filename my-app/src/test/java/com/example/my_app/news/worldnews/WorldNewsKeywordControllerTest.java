package com.example.my_app.news.worldnews;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class WorldNewsKeywordControllerTest {

  @Mock WorldNewsKeywordRepository keywordRepository;
  @Mock WorldNewsSearchTextRefresher searchTextRefresher;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        standaloneSetup(new WorldNewsKeywordController(keywordRepository, searchTextRefresher))
            .build();
  }

  @Test
  void list_returnsSortedKeywords() throws Exception {
    WorldNewsKeyword second = new WorldNewsKeyword();
    second.setId("b");
    second.setKeyword("gold");
    second.setOperator(WorldNewsKeywordOperator.OR);
    second.setSortOrder(1);
    WorldNewsKeyword first = new WorldNewsKeyword();
    first.setId("a");
    first.setKeyword("silver");
    first.setOperator(WorldNewsKeywordOperator.NOT);
    first.setSortOrder(0);
    when(keywordRepository.findAll(any(Sort.class))).thenReturn(List.of(first, second));

    mockMvc
        .perform(get("/api/world-news/keywords"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("a"))
        .andExpect(jsonPath("$[0].keyword").value("silver"))
        .andExpect(jsonPath("$[1].id").value("b"));
  }

  @Test
  void list_empty_returnsEmptyArray() throws Exception {
    when(keywordRepository.findAll(any(Sort.class))).thenReturn(List.of());

    mockMvc
        .perform(get("/api/world-news/keywords"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void create_persistsAndRefreshesSearchText() throws Exception {
    when(keywordRepository.save(any(WorldNewsKeyword.class)))
        .thenAnswer(
            inv -> {
              WorldNewsKeyword k = inv.getArgument(0);
              k.setId("kid1");
              return k;
            });

    mockMvc
        .perform(
            post("/api/world-news/keywords")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"keyword":"gold","operator":"OR","sort_order":1}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("kid1"))
        .andExpect(jsonPath("$.keyword").value("gold"))
        .andExpect(jsonPath("$.operator").value("OR"))
        .andExpect(jsonPath("$.sort_order").value(1));

    verify(searchTextRefresher).refresh();
  }

  @Test
  void create_rejectsBlankKeyword() throws Exception {
    mockMvc
        .perform(
            post("/api/world-news/keywords")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\"  \",\"operator\":\"OR\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void delete_removesAndRefreshes() throws Exception {
    when(keywordRepository.existsById("x")).thenReturn(true);

    mockMvc.perform(delete("/api/world-news/keywords/x")).andExpect(status().isNoContent());

    verify(keywordRepository).deleteById("x");
    verify(searchTextRefresher).refresh();
  }

  @Test
  void delete_missing_returns404() throws Exception {
    when(keywordRepository.existsById("missing")).thenReturn(false);

    mockMvc.perform(delete("/api/world-news/keywords/missing")).andExpect(status().isNotFound());
  }
}
