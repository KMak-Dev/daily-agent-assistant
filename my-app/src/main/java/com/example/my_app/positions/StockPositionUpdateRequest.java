package com.example.my_app.positions;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record StockPositionUpdateRequest(
    BigDecimal quantity, @JsonProperty("opened_at") Instant openedAt) {}
