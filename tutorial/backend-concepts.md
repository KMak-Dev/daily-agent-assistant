# Tutorial index

| Document | Purpose |
|----------|---------|
| **[Java tutorial for beginners](java-tutorial-for-beginners.md)** | Core Java (syntax, OOP, collections, exceptions, records, lambdas, `java.time`) before diving into frameworks. |
| **[Spring Boot tutorial](spring-boot-tutorial.md)** | Detailed Spring Boot guide after the Java primer (generic examples). |
| **This file (below)** | Maps Spring Boot concepts to the **`my-app/`** sources in this repository. |
| **[React tutorial](react-tutorial.md)** | Core React (JSX, hooks, effects, lists, Vite) for reading modern frontend code. |
| **[React in this repository](react-in-this-repo.md)** | Maps those React concepts to **`briefing-ui/`** and points to **`doc/frontend.md`**. |

---

# Spring Boot tutorial (concepts and where they appear)

This document explains Spring Boot features **as used in this Java module**. It does not describe product behavior; it maps **concepts → files and annotations** so you can read the code alongside Spring’s reference documentation.

**Stack reference:** Spring Boot **4.x**, Java **21**, Gradle, Spring Web MVC, Spring Data MongoDB, JUnit 5.

**Module paths:** Paths like `src/main/java/...` and `build.gradle` are relative to the **`my-app/`** Gradle project (from the repo root: `my-app/...`).

**Product docs (APIs, env vars, collections):** [doc/backend.md](../doc/backend.md) and [doc/briefing-api.md](../doc/briefing-api.md).

**Run the full stack:** From the repo root, `cp .env.example .env`, set keys as needed, then `docker compose up --build` (MongoDB + app on **8080**; see `docker-compose.yml`). To trigger the same analyze window as the optional cron job from your shell, use [scripts/run-daily-news-briefing.sh](../scripts/run-daily-news-briefing.sh) (Compose does not load `.env` into the script’s process—export variables or `source` `.env` as noted in `.env.example`).

---

## 1. Build and classpath starters

**Concept:** The Spring Boot Gradle plugin applies dependency management and packages a runnable jar. Starters bundle libraries so one dependency pulls in a coherent stack (web, data, test).

**Where:**

- `build.gradle` — `org.springframework.boot` plugin, `io.spring.dependency-management`, Java toolchain **21**.
- `implementation 'org.springframework.boot:spring-boot-starter-webmvc'` — servlet stack, JSON, `RestClient` support used by HTTP APIs.
- `implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'` — MongoDB driver, `MongoTemplate`/`MongoRepository` auto-configuration.
- `testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'` — testing support (e.g. `MockMvc` patterns).
- `test` task uses **JUnit Platform** (`useJUnitPlatform()`).

---

## 2. Application entry point and component scanning

**Concept:** `@SpringBootApplication` is a composed annotation: configuration, component scan (from the class’s package downward), and Spring Boot auto-configuration.

**Where:**

- `src/main/java/com/example/my_app/DailyApp.java` — `main` calls `SpringApplication.run(DailyApp.class, args)`.

All `@Component`, `@Service`, `@RestController`, `@Configuration`, etc. under `com.example.my_app` are discovered from this root.

---

## 3. Scheduling

**Concept:** `@EnableScheduling` registers scheduled task infrastructure. `@Scheduled` methods run on a thread pool according to fixed delay, cron, etc. Delays can come from property placeholders.

**Where:**

- `DailyApp.java` — `@EnableScheduling`.
- `src/main/java/com/example/my_app/news/worldnews/WorldNewsIngestScheduler.java` — `@Scheduled(initialDelayString = "...", fixedDelayString = "...")` reads `worldnews.ingest-*` from configuration.
- `src/main/java/com/example/my_app/news/dailybriefing/NewsDailyBriefingScheduler.java` — `@Scheduled(cron = "${news.daily-briefing.cron}", zone = "${news.daily-briefing.zone-id}")` runs the daily analyze + archive path when the feature is enabled (see §16).

---

## 4. Externalized configuration (`application.properties`)

**Concept:** Key-value configuration with optional **environment variable overrides** and **defaults** using `${NAME:default}`. Spring Boot 4 uses property namespaces such as `spring.mongodb.*` for MongoDB (see comments in the file).

**Where:**

- `src/main/resources/application.properties` — `spring.application.name`, `spring.mongodb.*`, and custom `worldnews.*` keys consumed by `@ConfigurationProperties`.

---

## 5. Type-safe configuration: `@ConfigurationProperties`

**Concept:** Binds a prefix of properties to an immutable or mutable type (here a **Java `record`**) so code depends on typed fields instead of raw `Environment` lookups.

**Where:**

- `src/main/java/com/example/my_app/news/worldnews/WorldNewsProperties.java` — `@ConfigurationProperties(prefix = "worldnews")` record; helper `hasApiKey()`.
- `src/main/java/com/example/my_app/news/xai/XaiProperties.java` — `@ConfigurationProperties(prefix = "xai")` for Grok base URL, model, API key, batch sizing, etc.
- `src/main/java/com/example/my_app/news/dailybriefing/NewsDailyBriefingProperties.java` — `@ConfigurationProperties(prefix = "news.daily-briefing")` for cron, zone id, day offset, and window length.

**Registration:** `@EnableConfigurationProperties(...)` on `@Configuration` classes so each record is a bean.

**Where:**

- `WorldNewsConfiguration.java` — `WorldNewsProperties`.
- `src/main/java/com/example/my_app/news/xai/XaiConfiguration.java` — `XaiProperties`.
- `src/main/java/com/example/my_app/news/dailybriefing/NewsDailyBriefingConfiguration.java` — `NewsDailyBriefingProperties`.

---

## 6. Java configuration: `@Configuration` and `@Bean`

**Concept:** Replace XML with Java: `@Configuration` classes define `@Bean` methods. Return values become singleton beans in the application context.

**Where:**

- `WorldNewsConfiguration.java` — `@Bean` method builds `RestClient` with a base URL for the World News HTTP API.
- `XaiConfiguration.java` — `@Bean` `@Qualifier("xaiRestClient")` builds `RestClient` for the xAI base URL.
- `src/main/java/com/example/my_app/news/analysis/NewsAnalyzePromptsConfiguration.java` — `@Bean` loads `classpath:prompts/news-analyze.yaml` into `NewsAnalyzePrompts` (SnakeYAML + `ResourceLoader`).
- `NewsDailyBriefingConfiguration.java` — enables `NewsDailyBriefingProperties` (no extra beans).
- `src/main/java/com/example/my_app/config/PaginationConfig.java` — `@Bean` returning `PageableHandlerMethodArgumentResolverCustomizer` to cap maximum page size for `Pageable` query parameters.

---

## 7. Qualifying beans (`@Qualifier`)

**Concept:** When multiple beans of the same type could exist, or you want to inject a specific named bean, use `@Qualifier("beanName")`. The default name for a `@Bean` method is the **method name** unless overridden.

**Where:**

- `src/main/java/com/example/my_app/news/worldnews/WorldNewsClient.java` — constructor parameter `@Qualifier("worldNewsRestClient") RestClient` matches the bean from `worldNewsRestClient()` in `WorldNewsConfiguration`.
- `src/main/java/com/example/my_app/news/xai/XaiResponsesClient.java` — `@Qualifier("xaiRestClient") RestClient` for Grok `POST /v1/responses` calls.

---

## 8. Stereotypes: `@RestController`, `@Service`, `@Component`

**Concept:** Specialized `@Component` types for clarity. `@RestController` is `@Controller` + `@ResponseBody` (return values serialized to HTTP body, typically JSON).

**Where:**

- **REST:** `StatusController.java`, `news/NewsController.java`, `news/analysis/NewsAnalyzeController.java`, `news/dailybriefing/NewsBriefingController.java`, `positions/StockPositionController.java`, `news/worldnews/WorldNewsKeywordController.java` (under `src/main/java/com/example/my_app/`).
- **Service:** `news/worldnews/WorldNewsIngestService.java`, `news/analysis/NewsAnalyzeService.java` (analyze pipeline + briefing archive upsert), `news/dailybriefing/NewsBriefingArchiveService.java` (under `src/main/java/com/example/my_app/`).
- **Component:** `news/worldnews/WorldNewsClient.java`, `news/worldnews/WorldNewsIngestScheduler.java`, `news/worldnews/WorldNewsSearchTextRefresher.java`, `news/dailybriefing/NewsDailyBriefingScheduler.java`, `news/dailybriefing/NewsDailyBriefingRunner.java`, `news/xai/XaiResponsesClient.java`, and related types under `news/worldnews/` (paths under `src/main/java/com/example/my_app/`).

---

## 9. Constructor injection

**Concept:** Spring resolves dependencies via a single constructor (recommended). Fields are `final`; the class is easier to test.

**Where:** All controllers and services in this module (e.g. `NewsController`, `NewsAnalyzeController`, `NewsBriefingController`, `StockPositionController`, `WorldNewsKeywordController`, `NewsAnalyzeService`, `WorldNewsIngestService`).

---

## 10. Web MVC: mapping, status codes, and request bodies

**Concept:** Map URLs and HTTP methods to methods. Control status with `@ResponseStatus` or `ResponseStatusException`. `consumes` / `produces` narrow content types.

**Where:**

- `@RequestMapping` class-level base path + `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` on methods — `NewsController`, `NewsAnalyzeController`, `NewsBriefingController`, `StockPositionController`, `WorldNewsKeywordController`, `StatusController`.
- `@RequestBody` — JSON to Java objects (records or POJOs), e.g. bulk create in `StockPositionController`, `NewsCreateRequest` in `NewsController`, keyword create in `WorldNewsKeywordController`.
- `@PathVariable` — path segments (e.g. `symbol`, `id`).
- `@RequestParam` — query parameters; `required = false` for optional filters; `name = "published_date"` for snake_case query names.
- `@DateTimeFormat(iso = ISO.DATE)` or `ISO.DATE_TIME` — binding query params to `LocalDate` / `Instant`.
- `@ResponseStatus(HttpStatus.CREATED)` etc. — default response status for successful handler return.
- `ResponseStatusException` — `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, etc. — used across controllers for validation and missing resources.

**Deferred result (optional pattern):**

- `StatusController` returns `Callable<Map<...>>` for async-style dispatching of the response (Spring MVC invokes the callable to produce the body).

---

## 11. Pagination and sorting (`Pageable`)

**Concept:** Spring Data Web binds `page`, `size`, `sort` query parameters to `Pageable`. `@PageableDefault` sets defaults when the client omits them. Repositories return `Page<T>`.

**Where:**

- `NewsController.java` — `list(Pageable pageable)` with `@PageableDefault(size = 20, sort = "publishedDate", direction = Sort.Direction.DESC)`.
- `NewsBriefingController.java` — paged `list(..., Pageable pageable)` with `@PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)` over archived briefings.
- `NewsItemRepository.java` — `MongoRepository` provides `findAll(Pageable)`.
- `src/main/java/com/example/my_app/news/dailybriefing/NewsDailyBriefingRepository.java` — paging queries for briefing archive lists.
- `src/main/java/com/example/my_app/news/NewsPageResponse.java` — maps `Page<NewsItem>` to a JSON-friendly DTO.
- `src/main/java/com/example/my_app/news/dailybriefing/NewsBriefingPageResponse.java` — same idea for `Page<NewsDailyBriefing>`.
- `PaginationConfig.java` — `setMaxPageSize(100)` on the pageable resolver.

---

## 12. Spring Data MongoDB: documents and repositories

**Concept:** Map classes to collections with `@Document`. `@Id` marks the primary key. Index and field mapping annotations shape the stored document. Extend `MongoRepository<Entity, IdType>` for CRUD and query-method derivation.

**Where:**

- `src/main/java/com/example/my_app/news/NewsItem.java` — `@Document(collection = "news_items")`, `@Indexed`, `@Field("published_date")`.
- `src/main/java/com/example/my_app/positions/StockPosition.java` — position entity and indexes as defined in source.
- `src/main/java/com/example/my_app/news/dailybriefing/NewsDailyBriefing.java` — `@Document(collection = "news_daily_briefings")`, compound unique index on `(time_zone, start_date, end_date)`.
- `NewsItemRepository.java`, `StockPositionRepository.java`, `WorldNewsKeywordRepository.java`, `NewsDailyBriefingRepository.java` — extend `MongoRepository<..., String>`; custom method names like `findByPublishedDateBetweenOrderByPublishedDateDesc`, `existsByUrl`, `findBySymbolIn`, window/key queries for briefings, etc.

---

## 13. Query by example (`Example`, `ExampleMatcher`)

**Concept:** Build a **probe** entity with only the fields you want to match; `ExampleMatcher` can ignore nulls and tune string matching.

**Where:**

- `NewsController.java` — `deleteByMatchingParams` builds a `NewsItem` probe, `ExampleMatcher.matching().withIgnoreNullValues()`, `newsItemRepository.findAll(Example.of(probe, matcher))` then `deleteAll`.
- `StockPositionController.java` — same pattern for `deleteByMatchingParams` on `StockPosition`.

---

## 14. Sorting without pagination

**Concept:** `Sort.by(Direction, "field")` passed to `findAll(Sort)` or repository methods.

**Where:**

- `StockPositionController.java` — `findAll(Sort.by(Sort.Direction.ASC, "symbol"))`.
- `WorldNewsKeywordController.java` / `WorldNewsSearchTextRefresher.java` — `Sort.by(Sort.Direction.ASC, "sortOrder", "id")` with `WorldNewsKeywordRepository`.

---

## 15. Data access exceptions → HTTP errors

**Concept:** Translate persistence exceptions to appropriate HTTP status in the controller layer.

**Where:**

- `StockPositionController.java` — `catch (DuplicateKeyException e)` maps to `ResponseStatusException(HttpStatus.CONFLICT, ...)`.

---

## 16. Conditional beans (`@ConditionalOnProperty`)

**Concept:** Register a bean only when a property matches, so features can be disabled via config (e.g. tests or production toggles).

**Where:**

- `WorldNewsIngestScheduler.java` — `@ConditionalOnProperty(prefix = "worldnews", name = "ingest-enabled", havingValue = "true")`.
- `NewsDailyBriefingScheduler.java` — `@ConditionalOnProperty(prefix = "news.daily-briefing", name = "enabled", havingValue = "true")` so the cron job is absent when daily briefing is off (default in `application.properties`).

---

## 17. HTTP clients: `RestClient`

**Concept:** Fluent synchronous HTTP client in Spring Framework 6+. Built via `RestClient.builder()`, then `get().uri(...).retrieve().body(Class)`.

**Where:**

- `WorldNewsConfiguration.java` — bean creation.
- `WorldNewsClient.java` — `restClient.get().uri(uri).retrieve().body(SearchNewsResponse.class)`; catches `RestClientResponseException` for logging.
- `XaiConfiguration.java` / `XaiResponsesClient.java` — `RestClient.post().uri(...).body(...).retrieve()` against xAI’s JSON API (see class for headers and response parsing).

**URI building:**

- `WorldNewsClient.java` — `UriComponentsBuilder.fromPath(...).queryParam(...)` for query strings.

---

## 18. Logging (SLF4J)

**Concept:** Use `LoggerFactory.getLogger(Class)` for structured logs without tying to a concrete logging implementation (Logback is typical on the classpath via starters).

**Where:**

- `WorldNewsIngestService.java`, `WorldNewsClient.java` — `debug` / `warn` around ingest and HTTP failures.

---

## 19. DTOs with Java records

**Concept:** Immutable carriers for API requests/responses and for external JSON shapes (e.g. API responses modeled as records).

**Where:**

- Request/response records under `news/`, `news/analysis/`, `news/dailybriefing/`, `news/worldnews/`, and `positions/` (e.g. `NewsCreateRequest`, `NewsAnalyzeRequest`, `NewsAnalyzeResponse`, `StockPositionItemRequest`, `WorldNewsKeywordCreateRequest`, `SearchNewsResponse`, `WorldNewsKeywordResponse`).

---

## 20. Testing the Spring context

### 20.1 Full context smoke test

**Concept:** `@SpringBootTest` loads the application context (similar to production). Combine with test-specific properties and bean overrides.

**Where:**

- `src/test/java/com/example/my_app/DailyAppTests.java` — `@SpringBootTest`, `@ActiveProfiles("test")`.
- `src/test/resources/application-test.properties` — `spring.autoconfigure.exclude=...` disables Mongo auto-configuration so tests run **without** a database; `worldnews.ingest-enabled=false` avoids scheduled side effects (daily briefing stays off unless a test enables it).

### 20.2 Replacing beans with Mockito (`@MockitoBean`)

**Concept:** Register a mock in place of a real repository (or client) for isolation.

**Where:**

- `DailyAppTests.java` — `@MockitoBean` on `NewsItemRepository`, `StockPositionRepository`, `WorldNewsKeywordRepository`.
- `src/test/java/com/example/my_app/news/worldnews/WorldNewsIngestSchedulerIT.java` — mocks `WorldNewsClient` and repositories; uses `@TestPropertySource(properties = {...})` to shorten scheduler delays and enable ingest for the test scenario.

### 20.3 Async verification (Awaitility)

**Concept:** Poll until an assertion passes, used when behavior is time-based (scheduled tasks).

**Where:**

- `WorldNewsIngestSchedulerIT.java` — `await().atMost(...).untilAsserted(() -> verify(worldNewsClient).searchNews(...))`.

### 20.4 Slice-style controller test with `MockMvc` (standalone)

**Concept:** Test a controller in isolation by constructing `MockMvc` with `standaloneSetup(controller)` and mocking dependencies—no full context required.

**Where:**

- `src/test/java/com/example/my_app/news/worldnews/WorldNewsKeywordControllerTest.java` — `@ExtendWith(MockitoExtension.class)`, `@Mock` repositories, `MockMvcRequestBuilders` / `MockMvcResultMatchers`.

### 20.5 Pure unit tests (no Spring)

**Concept:** JUnit + plain Java for algorithms and builders.

**Where:**

- `src/test/java/com/example/my_app/news/worldnews/WorldNewsSearchTextBuilderTest.java` — tests `WorldNewsSearchTextBuilder` without Spring.
- `src/test/java/com/example/my_app/news/worldnews/WorldNewsIngestServiceTest.java` — service logic with mocked collaborators (see file for style).
- `src/test/java/com/example/my_app/news/xai/XaiResponsesClientTest.java` — xAI client with mocked `RestClient`.
- `src/test/java/com/example/my_app/news/analysis/BriefingTextUtilsTest.java` — pure string utilities for briefing output.

---

## 21. Code quality (not Spring, but adjacent)

**Concept:** Spotless applies a formatter on Java sources as part of `check`.

**Where:**

- `build.gradle` — `com.diffplug.spotless`, Google Java Format, `spotlessCheck` hooked to `check`.

---

## Quick lookup table

| Topic | Primary locations |
|--------|-------------------|
| Bootstrapping | `DailyApp.java` |
| Mongo settings | `application.properties` |
| Typed config | `WorldNewsProperties.java`, `XaiProperties.java`, `NewsDailyBriefingProperties.java`, `*Configuration.java` under `news/worldnews`, `news/xai`, `news/dailybriefing` |
| REST APIs | `*Controller.java` under `news/` (incl. `analysis/`, `dailybriefing/`), `positions/`, `worldnews/` |
| Pagination | `NewsController.java`, `NewsBriefingController.java`, `PaginationConfig.java`, `NewsPageResponse.java`, `NewsBriefingPageResponse.java` |
| Mongo entities/repos | `NewsItem.java`, `StockPosition.java`, `NewsDailyBriefing.java`, `*Repository.java` |
| Example queries | `NewsController.java`, `StockPositionController.java` |
| Scheduling | `DailyApp.java`, `WorldNewsIngestScheduler.java`, `NewsDailyBriefingScheduler.java` |
| Conditional feature | `WorldNewsIngestScheduler.java`, `NewsDailyBriefingScheduler.java` |
| HTTP client | `WorldNewsConfiguration.java`, `WorldNewsClient.java`, `XaiConfiguration.java`, `XaiResponsesClient.java` |
| Service layer | `WorldNewsIngestService.java`, `WorldNewsSearchTextRefresher.java`, `NewsAnalyzeService.java`, `NewsBriefingArchiveService.java`, `NewsDailyBriefingRunner.java` |
| Classpath prompts | `resources/prompts/news-analyze.yaml`, `NewsAnalyzePromptsConfiguration.java` |
| Integration / unit tests | `src/test/java/...`, `application-test.properties` |

For authoritative semantics (every annotation attribute, every property key), use the [Spring Boot reference documentation](https://docs.spring.io/spring-boot/reference/) and [Spring Framework reference](https://docs.spring.io/spring-framework/reference/) for your Boot version.
