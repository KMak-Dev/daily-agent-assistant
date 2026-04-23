package com.example.my_app.news.dailybriefing;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class NewsBriefingControllerTest {

  @Mock NewsDailyBriefingRepository briefingRepository;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(new NewsBriefingController(briefingRepository)).build();
  }

  @Test
  void delete_exists_returns204() throws Exception {
    when(briefingRepository.existsById("abc")).thenReturn(true);

    mockMvc.perform(delete("/api/news/briefings/abc")).andExpect(status().isNoContent());

    verify(briefingRepository).deleteById("abc");
  }

  @Test
  void delete_missing_returns404() throws Exception {
    when(briefingRepository.existsById("abc")).thenReturn(false);

    mockMvc.perform(delete("/api/news/briefings/abc")).andExpect(status().isNotFound());
  }
}
