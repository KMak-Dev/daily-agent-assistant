# Backend overview

The backend is a **Spring Boot** application (`DailyApp`) on **Java 21**, with **Spring Data MongoDB** for persistence and **`@EnableScheduling`** for background jobs. It exposes a JSON HTTP API on port **8080** by default (see `docker-compose.yml`).

---

## Running the stack

From the repository root:

1. Copy environment template: `cp .env.example .env` and fill in API keys as needed.
2. Start MongoDB and the app: `docker compose up --build`.

The Compose file builds `my-app/` with the `Dockerfile` (multi-stage Gradle `bootJar`, then JRE 21). The app waits for MongoDB’s healthcheck before starting.

For local runs without Compose, point Spring at MongoDB using `spring.mongodb.*` or `SPRING_MONGODB_URI` (see `my-app/src/main/resources/application.properties`).

---

## High-level architecture

| Area | Role |
|------|------|
| **REST controllers** | CRUD and query endpoints under `/api/...` plus `GET /` for a simple liveness payload. |
| **MongoDB** | Stores news articles, portfolio positions, World News search keywords, and archived daily briefings. |
| **World News API** | Scheduled ingest pulls one search page per tick and inserts new rows into `news_items` (dedupe by `url`). |
| **xAI (Grok)** | On-demand summarization and briefing synthesis (`POST /api/news/analyze`); optional scheduled daily briefing when enabled. |

There is **no authentication** layer in this service; treat it as an internal or development API unless you add a reverse proxy and auth in front.

---

## MongoDB collections

| Collection | Document type | Notes |
|------------|---------------|--------|
| `news_items` | `NewsItem` | Article fields; optional `summary` filled by the analyze pipeline. Unique sparse index on `url`. |
| `stock_positions` | `StockPosition` | One row per ticker symbol; unique index on `symbol`. |
| `world_news_keywords` | `WorldNewsKeyword` | Keyword + `OR` / `NOT` operator + `sort_order`; drives search text when non-empty (see below). |
| `news_daily_briefings` | `NewsDailyBriefing` | Archived briefings; compound unique index on `(time_zone, start_date, end_date)`. |

---

## HTTP API summary

All JSON APIs use `application/json` unless noted. Spring Data pagination uses query parameters `page`, `size`, and `sort` where listed.

### Root

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Returns `{"status":"running"}` (async handler). |

### News items (`/api/news`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/news` | Paged list of all news items; default sort `publishedDate` descending. |
| `GET` | `/api/news/range?from=&to=` | Articles with `published_date >= from` and `published_date < to` (half-open `to`); `from` must be ≤ `to`. |
| `POST` | `/api/news` | Create one article. Body: `url`, `title`, `authors`, `content`, `published_date` (ISO date). |
| `DELETE` | `/api/news/{id}` | Delete by MongoDB id; `404` if missing. |
| `DELETE` | `/api/news` | Delete by query match (at least one of `url`, `title`, `authors`, `content`, `published_date`); `404` if nothing matched. |

**Analyze and archived briefings** — `POST /api/news/analyze` and all `/api/news/briefings/*` routes are documented in **[briefing-api.md](./briefing-api.md)** (windows, validation, persistence, cron).

### World News keywords (`/api/world-news/keywords`)

Used to build the **search-news `text` query** when this collection has at least one document (otherwise `worldnews.text` from config is used). Creating or deleting a keyword **refreshes** the in-memory search text.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/world-news/keywords` | List keywords ordered by `sort_order`, then `id`. |
| `POST` | `/api/world-news/keywords` | Body: `keyword` (required), `operator` (`OR` or `NOT`, required), optional `sort_order` (default `0`). Returns `201` with the saved row. |
| `DELETE` | `/api/world-news/keywords/{id}` | Delete by id; `204` on success, `404` if unknown. |

### Stock positions (`/api/positions`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/positions` | All positions sorted by `symbol` ascending. |
| `POST` | `/api/positions` | Bulk create: non-empty JSON array of `{ "symbol", "quantity", "opened_at" }`. Symbols normalized to upper case; duplicates in the request or existing DB symbols → `409`. |
| `PUT` | `/api/positions/{symbol}` | Patch `quantity` and/or `opened_at` (at least one required). Symbol in path is trimmed and uppercased. |
| `DELETE` | `/api/positions` | Delete all rows matching provided query params (at least one of `symbol`, `quantity`, `opened_at`, `last_updated`); `404` if none match. |

---

## Background jobs

### World News ingest

- Controlled by `worldnews.ingest-enabled` and `worldnews.api-key`. If the key is missing or ingest is disabled, runs no-op.
- **Schedule:** fixed delay after startup (`worldnews.ingest-initial-delay-ms`, default 60s), then every `worldnews.ingest-fixed-delay-ms` (default 1 hour).
- **Behavior:** one `search-news` call per run; new articles are inserted; existing `url` values are skipped.

### Daily briefing (optional)

- When `news.daily-briefing.enabled` is true, a cron job runs in `news.daily-briefing.zone-id` and upserts into `news_daily_briefings` with `lastSource = CRON`.
- Window and offset semantics match the analyze API; see [briefing-api.md § Persistence](./briefing-api.md#persistence).

---

## Configuration

Primary reference: `my-app/src/main/resources/application.properties`. Environment variable names are noted there (and in `.env.example` for Compose).

| Topic | Property prefix / env | Purpose |
|-------|------------------------|---------|
| MongoDB | `spring.mongodb.*`, `SPRING_MONGODB_URI` | Database connection; URI overrides discrete host/user/password settings. |
| World News | `worldnews.*`, `WORLD_NEWS_*` | API key, language, optional static `text` / filters, page size, ingest toggles and delays. |
| xAI | `xai.*`, `XAI_*` | API key, model, base URL, batch size, excerpt limit, optional default briefing prompt. |
| Daily briefing | `news.daily-briefing.*`, `NEWS_DAILY_BRIEFING_*` | Cron schedule, zone, day offset, window days, enable flag. |
| Errors | `server.error.include-message`, `SERVER_ERROR_INCLUDE_MESSAGE` | Whether to expose exception messages in JSON errors (`never` by default; use `always` only for debugging). |

Default briefing instructions also live in `my-app/src/main/resources/prompts/news-analyze.yaml`.

---

## Project layout (backend code)

```
my-app/
  src/main/java/com/example/my_app/
    DailyApp.java              # Entry point + @EnableScheduling
    StatusController.java
    news/
      NewsController.java
      NewsItem.java
      analysis/                # POST /api/news/analyze
      dailybriefing/           # Briefing entity, scheduler, GET /api/news/briefings/*
      worldnews/               # Ingest, keywords, World News client
      xai/                     # xAI HTTP client and config
    positions/
      StockPositionController.java
  src/main/resources/
    application.properties
    prompts/news-analyze.yaml
```

---

## Related documentation

- **[briefing-api.md](./briefing-api.md)** — Analyze request/response, briefing archive API, Mongo upsert rules, and cron window math.
