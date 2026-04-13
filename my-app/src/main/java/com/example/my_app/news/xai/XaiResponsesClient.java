package com.example.my_app.news.xai;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class XaiResponsesClient {

  private static final Logger log = LoggerFactory.getLogger(XaiResponsesClient.class);
  private static final int LOG_BODY_MAX_CHARS = 4000;

  private final RestClient restClient;
  private final XaiProperties properties;
  private final JsonMapper jsonMapper;

  public XaiResponsesClient(
      @Qualifier("xaiRestClient") RestClient restClient,
      XaiProperties properties,
      JsonMapper jsonMapper) {
    this.restClient = restClient;
    this.properties = properties;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Calls {@code POST /v1/responses} and returns the concatenated {@code output_text} from the
   * response body.
   */
  public String createResponse(String instructions, String input) {
    if (!properties.hasApiKey()) {
      log.warn("xAI request blocked: API key is not configured");
      throw new XaiClientException("xAI API key is not configured (set XAI_API_KEY)");
    }
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", properties.model());
    requestBody.put("input", input);
    if (instructions != null && !instructions.isBlank()) {
      requestBody.put("instructions", instructions);
    }
    // Do not send search_parameters: xAI returns 410 "Live search is deprecated" when that
    // object is present (even mode=off). We only pass ingested article text — no web search.

    final String jsonBody;
    try {
      jsonBody = jsonMapper.writeValueAsString(requestBody);
    } catch (JacksonException e) {
      log.warn("xAI request serialization failed: {}", e.toString());
      throw new XaiClientException("Failed to serialize xAI request", e);
    }

    final String responseJson;
    try {
      responseJson =
          restClient
              .post()
              .uri("/v1/responses")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(h -> h.setBearerAuth(properties.apiKey().trim()))
              .body(jsonBody)
              .retrieve()
              .body(String.class);
    } catch (RestClientResponseException e) {
      String body = e.getResponseBodyAsString();
      String bodyForLog = truncateForLog(body);
      log.warn(
          "xAI HTTP {} from POST /v1/responses (model={}): {}",
          e.getStatusCode().value(),
          properties.model(),
          bodyForLog);
      throw new XaiClientException("xAI HTTP " + e.getStatusCode().value() + ": " + body, e);
    }

    if (responseJson == null || responseJson.isBlank()) {
      log.warn("xAI returned an empty response body (model={})", properties.model());
      throw new XaiClientException("xAI returned an empty body");
    }

    final JsonNode root;
    try {
      root = jsonMapper.readTree(responseJson);
    } catch (JacksonException e) {
      log.warn(
          "xAI response is not valid JSON (model={}): {} — body prefix: {}",
          properties.model(),
          e.toString(),
          truncateForLog(responseJson));
      throw new XaiClientException("xAI response is not valid JSON", e);
    }

    if (root.hasNonNull("error")) {
      String err = root.get("error").toString();
      log.warn(
          "xAI error field in response (model={}): {}", properties.model(), truncateForLog(err));
      throw new XaiClientException("xAI error: " + err);
    }

    String status = root.path("status").asText("");
    if (!"completed".equals(status)) {
      log.warn(
          "xAI response status not completed (model={}, status={}) body prefix: {}",
          properties.model(),
          status,
          truncateForLog(responseJson));
      throw new XaiClientException("xAI response not completed (status=" + status + ")");
    }

    return extractOutputText(root);
  }

  private static String truncateForLog(String s) {
    if (s == null) {
      return "(null)";
    }
    String t = s.replace("\r\n", "\n").trim();
    if (t.length() <= LOG_BODY_MAX_CHARS) {
      return t;
    }
    return t.substring(0, LOG_BODY_MAX_CHARS) + "…(truncated)";
  }

  static String extractOutputText(JsonNode root) {
    StringBuilder sb = new StringBuilder();
    JsonNode output = root.path("output");
    if (!output.isArray()) {
      return "";
    }
    for (JsonNode item : output) {
      JsonNode content = item.path("content");
      if (!content.isArray()) {
        continue;
      }
      for (JsonNode c : content) {
        if ("output_text".equals(c.path("type").asText())) {
          sb.append(c.path("text").asText());
        }
      }
    }
    return sb.toString().trim();
  }
}
