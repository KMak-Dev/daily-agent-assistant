package com.example.my_app.news;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NewsCreateRequest(
		String url,
		String title,
		List<String> author,
		String content,
		@JsonProperty("published_date") LocalDate publishedDate) {
}
