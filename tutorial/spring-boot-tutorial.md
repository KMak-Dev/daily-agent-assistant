# Spring Boot tutorial (after the Java primer)

This guide is the **next step** after [Java tutorial for beginners](java-tutorial-for-beginners.md). It assumes you are comfortable with **packages**, **classes**, **interfaces**, **constructors**, **generics**, **`record`**, **exceptions**, and **collections**. If any of those feel shaky, revisit the Java doc first—Spring Boot is mostly **ordinary Java** plus a container that **wires objects together** for you.

**What you will learn here:** how a Spring Boot application starts, how **dependency injection** works, how to expose **REST APIs**, how **configuration** and **profiles** behave, how **scheduling** and **data access** fit typical services, and how **tests** are structured. Examples are **generic** (not tied to one business domain). When you want to see the same ideas **mapped to this repository’s `my-app/` tree**, read [backend-concepts.md](backend-concepts.md) in this folder.

**Versions used in examples:** **Java 21**, **Spring Boot 4.x**, **Gradle**. Dependency names follow Boot 4’s starter coordinates (for example `spring-boot-starter-webmvc` for the servlet web stack). If you use Boot 3, some coordinates differ slightly (`spring-boot-starter-web`); the **concepts** are the same.

---

## Part 1 — Why Spring Boot exists

### From `main` to a server

In the Java tutorial, you wrote:

```java
public static void main(String[] args) {
  // you constructed objects and called methods
}
```

A web service needs **many** objects working together: an HTTP server, JSON serializers, thread pools, a database client, transaction managers, your controllers, your services. Creating and linking all of that by hand in `main` becomes **error-prone** and **hard to test**.

**Spring** (the core framework) provides an **application context**: a registry of objects (**beans**) with **dependencies declared** (usually via constructors). The framework **constructs** the graph and **injects** collaborators.

**Spring Boot** adds **auto-configuration**: sensible defaults based on **what libraries are on the classpath**, so you get an embedded web server, JSON support, and data-store integration with **minimal boilerplate**. You still write normal Java; Boot removes most of the infrastructure ceremony.

### Inversion of Control (IoC) and Dependency Injection (DI)

- **Inversion of Control:** your classes do not `new` their long-lived dependencies; the **container** creates beans and calls your constructors.
- **Dependency injection:** dependencies arrive as **constructor parameters** (preferred) or setters/fields (less ideal).

Benefits you will notice quickly:

- **Testability:** in a test, you can substitute a **mock** repository or HTTP client without changing production code.
- **Single responsibility:** a service focuses on business rules; infrastructure is provided or configured elsewhere.

### Mental model: the `ApplicationContext`

At runtime, think of a **map** from bean **name/type** → **singleton instance** (default scope). When Spring needs a `OrderService`, it finds or creates `OrderService`, recursively resolving its constructor parameters (`OrderRepository`, `Clock`, …). **Cycles** in construction are an error—design collaborators as a **DAG** (directed acyclic graph).

You rarely call the context directly in application code; you declare types and let Spring call **your** code.

---

## Part 2 — Build layout and running the app

### Gradle project shape

A typical Boot layout:

```text
project/
  build.gradle
  settings.gradle
  src/main/java/...        ← your Java (compile classpath)
  src/main/resources/      ← configuration, static files (runtime classpath)
  src/test/java/...        ← tests
  src/test/resources/      ← test-only configuration
```

**`build.gradle`** (minimal sketch—not a copy of production—shows the idea):

```gradle
plugins {
  id 'java'
  id 'org.springframework.boot' version '4.0.5'
  id 'io.spring.dependency-management' version '1.1.7'
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories { mavenCentral() }

dependencies {
  implementation 'org.springframework.boot:spring-boot-starter-webmvc'
  testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
}

tasks.named('test') { useJUnitPlatform() }
```

- **`org.springframework.boot` plugin** — builds a **runnable fat JAR**, runs the app with `bootRun`, manages dependency versions via **BOM** (Bill of Materials) through `dependency-management`.
- **`spring-boot-starter-webmvc`** — servlet stack, **Spring MVC**, **Jackson** (JSON), embedded **Tomcat** (in Boot 4 naming; older docs may say `spring-boot-starter-web` for a similar bundle).

Run locally:

```bash
./gradlew bootRun
```

Package:

```bash
./gradlew bootJar
java -jar build/libs/*.jar
```

The fat JAR embeds dependencies and sets **`Main-Class`** to Spring Boot’s loader so `java -jar` works without a manual classpath.

### Starters (what you actually add)

Starters are **curated dependency bundles**. You declare one coordinate; Gradle pulls a **consistent** set of versions.

| Dependency (illustrative) | You get (high level) |
|---------------------------|-------------------------|
| `spring-boot-starter-webmvc` | Spring MVC, Jackson, Tomcat, `RestClient` support |
| `spring-boot-starter-data-mongodb` | Mongo driver, `MongoTemplate`, `MongoRepository` |
| `spring-boot-starter-data-jpa` | Hibernate, JDBC, `JpaRepository`, transactions |
| `spring-boot-starter-validation` | Bean Validation (`jakarta.validation`) for `@Valid` |
| `spring-boot-starter-actuator` | Health, metrics, operational endpoints |
| `spring-boot-starter-webmvc-test` / `…-test` | JUnit 5, AssertJ, Mockito, Spring Test |

Add only what you need; extra starters pull extra auto-configuration.

### Creating a fresh playground

Use [Spring Initializr](https://start.spring.io): **Gradle**, **Java 21**, dependencies **Spring Web** (or Web MVC per UI), **Validation**, **Spring Data MongoDB** or **JPA**, **Actuator** if you want ops endpoints. Download, unzip, open in your IDE, run `./gradlew bootRun`.

---

## Part 3 — Bootstrap: entry point and component scan

### The application class

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }
}
```

**`SpringApplication.run`** does three big things:

1. Creates a Spring **application context** (by default an **annotation-based** web context for servlet apps).
2. Registers **components** found by **classpath scanning**.
3. Applies **auto-configuration** (`@EnableAutoConfiguration`) based on classpath and properties.

### What `@SpringBootApplication` expands to

It is a **composed annotation** equivalent (conceptually) to:

- **`@SpringBootConfiguration`** — marks this class as a source of `@Bean` methods (`@Configuration` meta).
- **`@EnableAutoConfiguration`** — enables Boot’s auto-configuration machinery.
- **`@ComponentScan`** — scans **this class’s package and subpackages** for stereotypes (`@Component`, `@Service`, `@RestController`, …).

**Critical rule for beginners:** put `DemoApplication` in your **root package** (e.g. `com.example.demo`) and place **all** application code under **`com.example.demo.*`**. If you put a `@Service` in `com.other`, it will **not** be found unless you add `@ComponentScan` base packages explicitly.

### Optional: customize startup

```java
public static void main(String[] args) {
  SpringApplication app = new SpringApplication(DemoApplication.class);
  app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
  app.run(args);
}
```

**Command-line arguments** become **Spring properties**: `--server.port=9090` overrides `server.port` for that launch.

**`ApplicationRunner`** / **`CommandLineRunner`** beans execute **after** the context is ready—useful for one-time migrations or diagnostics (control order with `@Order`).

---

## Part 4 — Stereotypes and constructor injection

### Stereotypes Spring discovers

| Annotation | Meaning |
|------------|---------|
| `@Component` | Generic Spring-managed bean. |
| `@Service` | Same as `@Component`; signals **domain/service** layer in architecture diagrams. |
| `@Repository` | Persistence; on JPA, may add **exception translation** to Spring’s `DataAccessException` hierarchy. |
| `@RestController` | REST controller: `@Controller` + `@ResponseBody` on the class. |
| `@Configuration` | Class that declares **`@Bean`** methods (programmatic bean registration). |

### Constructor injection (the default style)

```java
@Service
public class OrderService {

  private final OrderRepository orders;
  private final Clock clock;

  public OrderService(OrderRepository orders, Clock clock) {
    this.orders = orders;
    this.clock = clock;
  }

  // ...
}
```

If there is **exactly one** constructor, you **do not need** `@Autowired` on it (since Spring Framework 4.3). Prefer **`final`** fields so dependencies are **immutable** after construction—this matches the “clear object state” mindset from the Java tutorial.

**Avoid field injection** (`@Autowired` on fields) in new code: it hides dependencies, complicates testing, and fights immutability.

---

## Part 5 — REST controllers (Spring MVC)

### A minimal read endpoint

```java
package com.example.demo.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @GetMapping("/{id}")
  public OrderDto get(@PathVariable String id) {
    return orderService.findById(id);
  }
}
```

- **`@RestController`** — return values are serialized to the **response body** (usually JSON via Jackson).
- **`@RequestMapping` on class** + **`@GetMapping` on method** — combined paths: `/api/orders/{id}`.
- **`@PathVariable`** — binds `{id}` to the parameter. If names differ, use `@PathVariable("id")`.

### Status codes and “not found”

When a resource might be missing, return **404** explicitly:

```java
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@GetMapping("/{id}")
public OrderDto get(@PathVariable String id) {
  return orderService
      .findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
}
```

`ResponseStatusException` is convenient in controllers; for richer APIs, centralize errors (next section).

### Request body + validation

Use a **`record`** for JSON request bodies (immutable DTO):

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(@NotBlank String customerId, @Positive int quantity) {}
```

Controller:

```java
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public OrderDto create(@Valid @RequestBody CreateOrderRequest request) {
  return orderService.create(request);
}
```

Add **`spring-boot-starter-validation`** on the classpath. **`@Valid`** triggers validation; violations become **400 Bad Request** with a **problem** body by default in recent Spring versions.

### Query parameters and headers

```java
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@GetMapping
public Page<OrderDto> search(
    @RequestParam(required = false) String customerId,
    @RequestHeader(name = "X-Request-Id", required = false) String requestId,
    Pageable pageable) {
  return orderService.search(customerId, requestId, pageable);
}
```

**`Pageable`** (Spring Data) binds `page`, `size`, `sort` query parameters when **`spring-boot-starter-data-*`** is present; cap max page size in production (see Part 8).

### `ResponseEntity` when status varies

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@GetMapping("/{id}")
public ResponseEntity<OrderDto> get(@PathVariable String id) {
  return orderService
      .findById(id)
      .map(ResponseEntity::ok)
      .orElse(ResponseEntity.notFound().build());
}
```

### CORS (browser clients)

For browser `fetch` from another origin, configure allowed origins via **`@CrossOrigin`** on controller/method or a **`WebMvcConfigurer#addCorsMappings`** bean. Avoid `*` with credentials.

---

## Part 6 — Centralized error handling

Controllers stay thin if you move repeated error mapping to **`@RestControllerAdvice`**:

```java
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handle(ResponseStatusException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
    pd.setTitle("Request failed");
    return pd;
  }
}
```

**`ProblemDetail`** (RFC 7807-style) gives consistent JSON for errors. Tune **`server.error.include-*`** in production so you do not leak stack traces or internal messages to clients.

---

## Part 7 — Configuration: properties, profiles, precedence

### Where configuration lives

- **`src/main/resources/application.properties`** or **`application.yml`**
- **Profile-specific:** `application-dev.yml`, activated with **`spring.profiles.active=dev`**
- **Environment variables** and **system properties** (common in containers)

### Defaults and placeholders

```properties
server.port=${PORT:8080}
app.downstream.timeout-ms=${DOWNSTREAM_TIMEOUT_MS:5000}
```

**`${NAME:default}`** reads `NAME` from the environment (or other property sources) or uses `default`.

### Relaxed binding (why `APP_MAIL_HOST` works)

For **`@ConfigurationProperties(prefix = "app.mail")`**, Spring maps **kebab-case**, **camelCase**, and **SCREAMING_SNAKE_CASE** env vars to the same logical properties. Exact rules are in the Boot reference under **Relaxed Binding**.

### Precedence (mental model)

Roughly: **command line** → **Java system properties** → **OS environment** → **`application-{profile}.*`** → **`application.*`**. When debugging “why is my property ignored?”, check whether a **higher-precedence** source overrides it.

### Secrets

Never commit real API keys. Inject secrets via **environment variables**, a **secret manager**, or **mounted files** in orchestrators. Local **`.env`** files are often shell/IDE concerns; Spring does not read `.env` unless you add tooling—Docker Compose and Kubernetes typically inject **environment variables** into the JVM process.

---

## Part 8 — Type-safe configuration with `@ConfigurationProperties`

Instead of scattering **`@Value("${...}")`**, bind a **prefix** to one type—especially nice with **`record`**:

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
    @DefaultValue("localhost") String host, @DefaultValue("25") int port, boolean enabled) {}
```

Register the type as a bean, for example:

```java
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {}
```

At injection sites, ask for **`MailProperties`** like any other bean.

### Nested and collection properties

YAML:

```yaml
app:
  mail:
    host: smtp.example.com
    headers:
      X-Pool: batch
```

Java:

```java
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String host, Map<String, String> headers) {}
```

### Validation at startup

Annotate the properties type with **`@Validated`** and use **`@NotNull`**, **`@Min`**, etc., so misconfiguration **fails fast** on boot.

### `@Value` vs `@ConfigurationProperties`

| Approach | Good for |
|----------|----------|
| `@Value("${one.flag}")` | Single flags, simple defaults |
| `@ConfigurationProperties` | Groups of related settings, documentation, validation |

---

## Part 9 — Java configuration: `@Bean`, qualifiers, conditions

### When to write `@Bean` methods

Use explicit **`@Bean`** methods when:

- Auto-configuration does not expose what you need.
- You construct a **third-party** object (`RestClient`, `ObjectMapper` copy, thread pool).
- You want **one shared instance** in the context.

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

  @Bean
  RestClient downstreamRestClient(@Value("${app.downstream.base-url}") String baseUrl) {
    return RestClient.builder().baseUrl(baseUrl).build();
  }
}
```

### Multiple beans of the same type

- **`@Qualifier("beanName")`** on a constructor parameter matches the **`@Bean` method name** by default.
- **`@Primary`** marks a **default** when several candidates exist—use sparingly; explicit qualifiers are clearer.

### Conditional beans

**`@ConditionalOnProperty`**, **`@ConditionalOnClass`**, **`@ConditionalOnMissingBean`**, and friends register beans only when conditions hold—essential for **optional integrations** and **tests**.

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobsConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true")
  public ReportJob reportJob() {
    return new ReportJob();
  }
}
```

### `RestClient` vs `WebClient`

- **`RestClient`** — synchronous, blocking HTTP client (Spring Framework 6+); straightforward for internal calls.
- **`WebClient`** — reactive; use with **WebFlux** when the stack is reactive end-to-end.

---

## Part 10 — Scheduling

### Enable scheduling once

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {
  // main unchanged
}
```

### Fixed delay vs fixed rate vs cron

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Poller {

  @Scheduled(fixedDelayString = "${app.poll.delay-ms:60000}")
  public void afterPreviousCompletes() {
    // runs delay ms after the prior invocation finishes
  }

  @Scheduled(fixedRateString = "60000")
  public void everySixtySecondsFromStart() {
    // can overlap if work exceeds 60s unless you serialize differently
  }

  @Scheduled(cron = "${app.report.cron:0 0 7 * * *}", zone = "${app.report.zone:UTC}")
  public void daily() {
    // six-field cron by default in Spring: second, minute, hour, day of month, month, day of week
  }
}
```

### Thread pool and isolation

Default scheduling uses a **single-thread** executor unless you configure a **`TaskScheduler`**. Long tasks **block** subsequent triggers—size pools explicitly for production workloads.

### Distributed deployments

If you run **multiple replicas**, `@Scheduled` runs on **each** instance unless you add **leader election**, **distributed locks**, or move scheduling to an **external scheduler** (Kubernetes `CronJob`, cloud scheduler). Design jobs to be **idempotent** or **safe to skip**.

---

## Part 11 — Spring Data: repositories, pagination, transactions

### Interface-based repositories

You declare an interface extending **`MongoRepository<Entity, ID>`** or **`JpaRepository<Entity, ID>`**. Spring generates an implementation at runtime.

```java
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {

  Optional<Order> findByCustomerIdAndId(String customerId, String id);

  Page<Order> findByCustomerId(String customerId, Pageable pageable);
}
```

**Derived query methods** encode simple queries (`And`, `Or`, `Between`, `OrderBy`, …). See Spring Data reference for the full grammar.

### Domain types (sketch)

**MongoDB:**

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "orders")
public class Order {

  @Id private String id;

  @Field("customer_id")
  private String customerId;

  private int quantity;

  // getters/setters or accessors as you prefer; records possible with some trade-offs for persistence
}
```

**JPA** would use **`@Entity`**, **`@Table`**, **`@Id`**, **`@GeneratedValue`**—different annotations, same repository idea.

### Pagination in the controller

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderQueryController {

  private final OrderRepository orders;

  public OrderQueryController(OrderRepository orders) {
    this.orders = orders;
  }

  @GetMapping("/api/orders")
  public Page<OrderDto> list(
      @RequestParam String customerId,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return orders.findByCustomerId(customerId, pageable).map(OrderDto::fromEntity);
  }
}
```

**Cap `size`** with **`PageableHandlerMethodArgumentResolverCustomizer`** to prevent accidental `size=100000` load.

### Transactions

Put **`@Transactional`** on **service** methods (not controllers) for JPA-style units of work. Keep transactions **short**—do not call slow remote HTTP APIs inside a transaction if you can avoid it.

### `Example` queries (optional)

For simple “match non-null fields” patterns, **`Example.of(probe, matcher)`** can express dynamic filters without writing query strings—useful for admin tooling, not for every read path.

---

## Part 12 — Testing: slices vs full context

### Pick the smallest effective test

| Style | What loads | Best for |
|-------|------------|----------|
| Plain JUnit + mocks | Nothing Spring | Pure logic, builders, mappers |
| `@WebMvcTest` | MVC slice | Controller mapping, JSON, status codes |
| `@DataJpaTest` / `@DataMongoTest` | Persistence slice | Repositories against embedded/test DB |
| **`@SpringBootTest`** | Full (or near-full) context | Integration flows, multi-bean behavior |

### Smoke test the context

```java
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DemoApplicationTests {

  @Test
  void contextLoads() {}
}
```

### Replacing beans for tests

When MongoDB or HTTP clients would make tests slow or flaky, register **mocks** instead of real beans. In Boot 3.4+, **`@MockitoBean`** is common on fields in test classes; older examples may show **`@MockBean`** (same idea, evolving API names—check your Boot version’s docs).

### `MockMvc`

**Slice test:**

```java
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean OrderService orderService;

  @Test
  void missingOrderIs404() throws Exception {
    when(orderService.findById("nope")).thenReturn(Optional.empty());
    mvc.perform(get("/api/orders/nope")).andExpect(status().isNotFound());
  }
}
```

**Standalone** `MockMvc` (no Spring context) is also possible for ultra-fast controller tests when you wire the controller manually.

### Test properties

In **`src/test/resources/application-test.properties`**, exclude auto-config you do not need (Mongo, JDBC) so tests start **without** Docker unless you want integration coverage.

### Integration tests with real infrastructure

**Testcontainers** runs real databases/brokers in Docker during tests; combine with **`@DynamicPropertySource`** to inject connection URLs into Spring’s `Environment`.

---

## Part 13 — Operations: Actuator, logging, shutdown

### Actuator

Add **`spring-boot-starter-actuator`**. Common endpoints:

- **`/actuator/health`** — liveness/readiness when configured for orchestrators.
- **`/actuator/metrics`**, **`/actuator/prometheus`** — observability (with Micrometer registries).

Secure actuator in production (separate port, network policy, or Spring Security). Recent Boot versions **do not expose** sensitive endpoints over HTTP by default.

### Logging

Use **SLF4J** (`LoggerFactory.getLogger(MyClass.class)`). Logback is the default implementation.

```properties
logging.level.root=INFO
logging.level.com.example.demo=DEBUG
```

### Graceful shutdown

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

Helps load balancers and Kubernetes **drain** in-flight requests before exit.

---

## Part 14 — Debugging auto-configuration

When beans are missing or duplicated:

- Run with **`--debug`** or raise **`logging.level.org.springframework.boot.autoconfigure=DEBUG`** temporarily.
- Read the **condition evaluation report** Boot prints on failure—it explains which `@Conditional*` failed.

---

## Part 15 — Learning path with this repository

1. Finish [Java tutorial for beginners](java-tutorial-for-beginners.md).
2. Build a toy service from this document (Initializr + one controller + one in-memory or Mongo path).
3. Open **[backend-concepts.md](backend-concepts.md)** in this folder to see **concept → file** mapping for the **`my-app/`** module (World News ingest, REST APIs, Mongo repositories, scheduling, tests).
4. Read product behavior in **`doc/backend.md`** and **`doc/briefing-api.md`** when you work on this specific product.

---

## Quick reference

| I need… | Reach for… |
|---------|------------|
| HTTP API | `@RestController`, `@GetMapping`, … |
| JSON body → Java | `@RequestBody` + DTO `record` |
| Validate input | `spring-boot-starter-validation`, `@Valid`, Bean Validation annotations |
| Config keys | `application.properties`, profiles, env vars |
| Typed config | `@ConfigurationProperties` + `@EnableConfigurationProperties` |
| Third-party object | `@Configuration` + `@Bean` |
| Two `RestClient` beans | `@Bean` names + `@Qualifier` |
| Feature flag bean | `@ConditionalOnProperty` |
| Background work | `@EnableScheduling`, `@Scheduled` |
| Database CRUD | `MongoRepository` / `JpaRepository` |
| Page of results | `Pageable`, `Page<T>`, `@PageableDefault` |
| Integration test | `@SpringBootTest`, test properties, `@MockitoBean` |
| Ops endpoints | `spring-boot-starter-actuator` |

---

## Authoritative documentation

- [Spring Boot reference](https://docs.spring.io/spring-boot/reference/) — properties, auto-configuration list, deployment.
- [Spring Framework — Servlet Web](https://docs.spring.io/spring-framework/reference/web/webmvc.html) — MVC, `RestClient`.
- [Spring Data Commons](https://docs.spring.io/spring-data/commons/reference/) — repositories, pagination, `Pageable`.

Spring evolves quickly; when examples disagree with your exact Boot minor version, **trust the reference** for annotation attributes and property keys.
