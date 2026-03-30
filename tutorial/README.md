# Spring Boot tutorial (concepts and where they appear)

This document explains Spring Boot features **as used in this Java module**. It does not describe product behavior; it maps **concepts → files and annotations** so you can read the code alongside Spring’s reference documentation.

**Stack reference:** Spring Boot **4.x**, Java **21**, Gradle, Spring Web MVC, Spring Data MongoDB, JUnit 5.

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

**Registration:** `@EnableConfigurationProperties(WorldNewsProperties.class)` on a `@Configuration` class so the record is a bean.

**Where:**

- `src/main/java/com/example/my_app/news/worldnews/WorldNewsConfiguration.java`.

---

## 6. Java configuration: `@Configuration` and `@Bean`

**Concept:** Replace XML with Java: `@Configuration` classes define `@Bean` methods. Return values become singleton beans in the application context.

**Where:**

- `WorldNewsConfiguration.java` — `@Bean` method builds `RestClient` with a base URL for the World News HTTP API.
- `src/main/java/com/example/my_app/config/PaginationConfig.java` — `@Bean` returning `PageableHandlerMethodArgumentResolverCustomizer` to cap maximum page size for `Pageable` query parameters.

---

## 7. Qualifying beans (`@Qualifier`)

**Concept:** When multiple beans of the same type could exist, or you want to inject a specific named bean, use `@Qualifier("beanName")`. The default name for a `@Bean` method is the **method name** unless overridden.

**Where:**

- `src/main/java/com/example/my_app/news/worldnews/WorldNewsClient.java` — constructor parameter `@Qualifier("worldNewsRestClient") RestClient` matches the bean from `worldNewsRestClient()` in `WorldNewsConfiguration`.

---

## 8. Stereotypes: `@RestController`, `@Service`, `@Component`

**Concept:** Specialized `@Component` types for clarity. `@RestController` is `@Controller` + `@ResponseBody` (return values serialized to HTTP body, typically JSON).

**Where:**

- **REST:** `src/main/java/com/example/my_app/StatusController.java`, `src/main/java/com/example/my_app/news/NewsController.java`, `src/main/java/com/example/my_app/positions/StockPositionController.java`, `src/main/java/com/example/my_app/news/worldnews/WorldNewsKeywordController.java`.
- **Service:** `src/main/java/com/example/my_app/news/worldnews/WorldNewsIngestService.java` — `@Service`.
- **Component:** `WorldNewsClient.java`, `WorldNewsIngestScheduler.java`, `WorldNewsSearchTextRefresher.java`, and related types under `news/worldnews/` using `@Component` where applicable.

---

## 9. Constructor injection

**Concept:** Spring resolves dependencies via a single constructor (recommended). Fields are `final`; the class is easier to test.

**Where:** All controllers and services in this module (e.g. `NewsController`, `StockPositionController`, `WorldNewsIngestService`, `WorldNewsKeywordController`).

---

## 10. Web MVC: mapping, status codes, and request bodies

**Concept:** Map URLs and HTTP methods to methods. Control status with `@ResponseStatus` or `ResponseStatusException`. `consumes` / `produces` narrow content types.

**Where:**

- `@RequestMapping` class-level base path + `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` on methods — `NewsController`, `StockPositionController`, `WorldNewsKeywordController`, `StatusController`.
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
- `NewsItemRepository.java` — `MongoRepository` provides `findAll(Pageable)`.
- `src/main/java/com/example/my_app/news/NewsPageResponse.java` — maps `Page<NewsItem>` to a JSON-friendly DTO.
- `PaginationConfig.java` — `setMaxPageSize(100)` on the pageable resolver.

---

## 12. Spring Data MongoDB: documents and repositories

**Concept:** Map classes to collections with `@Document`. `@Id` marks the primary key. Index and field mapping annotations shape the stored document. Extend `MongoRepository<Entity, IdType>` for CRUD and query-method derivation.

**Where:**

- `src/main/java/com/example/my_app/news/NewsItem.java` — `@Document(collection = "news_items")`, `@Indexed`, `@Field("published_date")`.
- `src/main/java/com/example/my_app/positions/StockPosition.java` — position entity and indexes as defined in source.
- `NewsItemRepository.java`, `StockPositionRepository.java`, `src/main/java/com/example/my_app/news/worldnews/WorldNewsKeywordRepository.java` — extend `MongoRepository<..., String>`; custom method names like `findByPublishedDateBetweenOrderByPublishedDateDesc`, `existsByUrl`, `findBySymbolIn`, etc.

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

---

## 17. HTTP clients: `RestClient`

**Concept:** Fluent synchronous HTTP client in Spring Framework 6+. Built via `RestClient.builder()`, then `get().uri(...).retrieve().body(Class)`.

**Where:**

- `WorldNewsConfiguration.java` — bean creation.
- `WorldNewsClient.java` — `restClient.get().uri(uri).retrieve().body(SearchNewsResponse.class)`; catches `RestClientResponseException` for logging.

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

- Request/response records under `news/` and `news/worldnews/` and `positions/` (e.g. `NewsCreateRequest`, `StockPositionItemRequest`, `StockPositionUpdateRequest`, `WorldNewsKeywordCreateRequest`, `SearchNewsResponse`, `WorldNewsKeywordResponse`).

---

## 20. Testing the Spring context

### 20.1 Full context smoke test

**Concept:** `@SpringBootTest` loads the application context (similar to production). Combine with test-specific properties and bean overrides.

**Where:**

- `src/test/java/com/example/my_app/DailyAppTests.java` — `@SpringBootTest`, `@ActiveProfiles("test")`.
- `src/test/resources/application-test.properties` — `spring.autoconfigure.exclude=...` disables Mongo auto-configuration so tests run **without** a database; `worldnews.ingest-enabled=false` avoids scheduled side effects.

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
| Typed config | `WorldNewsProperties.java`, `WorldNewsConfiguration.java` |
| REST APIs | `*Controller.java` under `news/`, `positions/`, `worldnews/` |
| Pagination | `NewsController.java`, `PaginationConfig.java`, `NewsPageResponse.java` |
| Mongo entities/repos | `NewsItem.java`, `StockPosition.java`, `*Repository.java` |
| Example queries | `NewsController.java`, `StockPositionController.java` |
| Scheduling | `DailyApp.java`, `WorldNewsIngestScheduler.java` |
| Conditional feature | `WorldNewsIngestScheduler.java` |
| HTTP client | `WorldNewsConfiguration.java`, `WorldNewsClient.java` |
| Service layer | `WorldNewsIngestService.java`, `WorldNewsSearchTextRefresher.java` |
| Integration / unit tests | `src/test/java/...`, `application-test.properties` |

For authoritative semantics (every annotation attribute, every property key), use the [Spring Boot reference documentation](https://docs.spring.io/spring-boot/reference/) and [Spring Framework reference](https://docs.spring.io/spring-framework/reference/) for your Boot version.
