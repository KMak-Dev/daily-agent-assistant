package com.example.my_app.news.xai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class XaiResponsesClientTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void extractOutputText_concatenatesOutputTextParts() throws Exception {
    String json =
        """
        {
          "status": "completed",
          "output": [
            {
              "content": [
                { "type": "output_text", "text": "Hello " },
                { "type": "output_text", "text": "world." }
              ]
            }
          ]
        }
        """;
    assertThat(XaiResponsesClient.extractOutputText(jsonMapper.readTree(json)))
        .isEqualTo("Hello world.");
  }
}
