package com.example.my_app.positions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/positions")
public class StockPositionController {

  private final StockPositionRepository stockPositionRepository;

  public StockPositionController(StockPositionRepository stockPositionRepository) {
    this.stockPositionRepository = stockPositionRepository;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockPosition> listAll() {
    return stockPositionRepository.findAll(Sort.by(Sort.Direction.ASC, "symbol"));
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public List<StockPosition> createBulk(@RequestBody List<StockPositionItemRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Request body must be a non-empty JSON array");
    }

    List<String> normalizedSymbols = new ArrayList<>(requests.size());
    List<StockPosition> toSave = new ArrayList<>(requests.size());
    Instant now = Instant.now();

    for (int i = 0; i < requests.size(); i++) {
      StockPositionItemRequest req = requests.get(i);
      if (req.symbol() == null || req.symbol().isBlank()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Each position must have a non-blank symbol (index " + i + ")");
      }
      if (req.quantity() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Each position must have quantity (index " + i + ")");
      }
      if (req.openedAt() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Each position must have opened_at (index " + i + ")");
      }
      String sym = req.symbol().trim().toUpperCase(Locale.ROOT);
      normalizedSymbols.add(sym);

      StockPosition entity = new StockPosition();
      entity.setSymbol(sym);
      entity.setQuantity(req.quantity());
      entity.setOpenedAt(req.openedAt());
      entity.setLastUpdated(now);
      toSave.add(entity);
    }

    Set<String> seen = new HashSet<>();
    for (String sym : normalizedSymbols) {
      if (!seen.add(sym)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Duplicate symbol in request: " + sym);
      }
    }

    List<StockPosition> existing = stockPositionRepository.findBySymbolIn(normalizedSymbols);
    if (!existing.isEmpty()) {
      String conflict =
          existing.stream()
              .map(StockPosition::getSymbol)
              .sorted()
              .collect(Collectors.joining(", "));
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Position(s) already exist for symbol(s): " + conflict);
    }

    try {
      return stockPositionRepository.saveAll(toSave);
    } catch (DuplicateKeyException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A position for one of these symbols already exists", e);
    }
  }

  @PutMapping(
      value = "/{symbol}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public StockPosition update(
      @PathVariable String symbol, @RequestBody StockPositionUpdateRequest request) {
    if (symbol == null || symbol.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol must not be blank");
    }
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
    }
    if (request.quantity() == null && request.openedAt() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Provide at least one of quantity or opened_at");
    }

    String sym = symbol.trim().toUpperCase(Locale.ROOT);
    StockPosition entity =
        stockPositionRepository
            .findBySymbol(sym)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (request.quantity() != null) {
      entity.setQuantity(request.quantity());
    }
    if (request.openedAt() != null) {
      entity.setOpenedAt(request.openedAt());
    }
    entity.setLastUpdated(Instant.now());
    return stockPositionRepository.save(entity);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteByMatchingParams(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) BigDecimal quantity,
      @RequestParam(name = "opened_at", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant openedAt,
      @RequestParam(name = "last_updated", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant lastUpdated) {
    boolean hasSymbol = symbol != null && !symbol.isBlank();
    boolean hasQuantity = quantity != null;
    boolean hasOpenedAt = openedAt != null;
    boolean hasLastUpdated = lastUpdated != null;
    if (!hasSymbol && !hasQuantity && !hasOpenedAt && !hasLastUpdated) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Provide at least one query parameter to match");
    }

    StockPosition probe = new StockPosition();
    if (hasSymbol) {
      probe.setSymbol(symbol.trim().toUpperCase(Locale.ROOT));
    }
    if (hasQuantity) {
      probe.setQuantity(quantity);
    }
    if (hasOpenedAt) {
      probe.setOpenedAt(openedAt);
    }
    if (hasLastUpdated) {
      probe.setLastUpdated(lastUpdated);
    }

    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    List<StockPosition> matches = stockPositionRepository.findAll(Example.of(probe, matcher));
    if (matches.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    stockPositionRepository.deleteAll(matches);
  }
}
