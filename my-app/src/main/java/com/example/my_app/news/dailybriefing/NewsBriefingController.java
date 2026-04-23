package com.example.my_app.news.dailybriefing;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/news/briefings")
public class NewsBriefingController {

  private final NewsDailyBriefingRepository briefingRepository;

  public NewsBriefingController(NewsDailyBriefingRepository briefingRepository) {
    this.briefingRepository = briefingRepository;
  }

  /**
   * Lists archived briefings (newest {@code updatedAt} first). Optional filters: IANA {@code
   * timeZone}, and inclusive {@code startDate} range on the stored window start.
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public NewsBriefingPageResponse list(
      @RequestParam(required = false) String timeZone,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    if ((from == null) != (to == null)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Provide both 'from' and 'to' or neither (inclusive range on startDate)");
    }
    if (from != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be on or before 'to'");
    }

    String tz = timeZone != null ? timeZone.trim() : "";
    boolean hasTz = !tz.isEmpty();
    boolean hasRange = from != null;

    Page<NewsDailyBriefing> page;
    if (hasTz && hasRange) {
      page =
          briefingRepository.findByTimeZoneAndStartDateUtcBetween(
              tz, utcStartOfDay(from), utcStartOfDay(to), pageable);
    } else if (hasTz) {
      page = briefingRepository.findByTimeZone(tz, pageable);
    } else if (hasRange) {
      page =
          briefingRepository.findByStartDateUtcBetween(
              utcStartOfDay(from), utcStartOfDay(to), pageable);
    } else {
      page = briefingRepository.findAll(pageable);
    }

    return NewsBriefingPageResponse.from(page);
  }

  /** Matches how {@link LocalDate} is persisted on {@code start_date} (UTC midnight instant). */
  private static Date utcStartOfDay(LocalDate d) {
    return Date.from(d.atStartOfDay(ZoneOffset.UTC).toInstant());
  }

  /**
   * Returns the single archived row for the same key used on upsert ({@code time_zone}, {@code
   * start_date}, {@code end_date}).
   */
  @GetMapping(value = "/window", produces = MediaType.APPLICATION_JSON_VALUE)
  public NewsDailyBriefing getByWindow(
      @RequestParam String timeZone,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    String tz = timeZone.trim();
    if (tz.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeZone is required");
    }
    Optional<NewsDailyBriefing> found =
        briefingRepository.findByTimeZoneAndStartDateAndEndDate(tz, startDate, endDate);
    return found.orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Briefing not found"));
  }

  @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public NewsDailyBriefing getById(@PathVariable String id) {
    return briefingRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Briefing not found"));
  }

  /** Removes one archived briefing row by MongoDB id. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    if (!briefingRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Briefing not found");
    }
    briefingRepository.deleteById(id);
  }
}
