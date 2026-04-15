# Briefing-related HTTP API

JSON over HTTP unless noted. Date fields in bodies and query parameters use ISO-8601 calendar dates (`YYYY-MM-DD`). Instants in JSON responses use ISO-8601 date-time (e.g. `2026-04-13T12:34:56.789Z`).

---

## Generate and archive a briefing

### `POST /api/news/analyze`

Runs the news pipeline for a date window: loads articles from MongoDB, ensures per-article summaries (batched xAI calls when needed), then produces a portfolio-aware briefing (xAI). On success, **upserts** one row in the `news_daily_briefings` collection for the same `(timeZone, startDate, endDate)` window (see [Persistence](#persistence)).

**Request body** (`application/json`)

| Field | Type | Required | Description |
|--------|------|----------|-------------|
| `startDate` | string (date) | yes | Inclusive lower bound for `publishedDate` on articles. |
| `endDate` | string (date) | no | **Exclusive** upper bound: articles match `publishedDate >= startDate` and `publishedDate < endDate`. If omitted, defaults to the start of **tomorrow** in the resolved `timeZone` (so a single “today” style run is possible when paired with an appropriate `startDate`). |
| `timeZone` | string | no | IANA time zone id (e.g. `America/New_York`). Defaults to `UTC` if null or blank. |
| `batchSize` | integer | no | Overrides `xai.batch-size` for summarization batches. Must be ≥ 1 if set. |
| `refreshSummaries` | boolean | no | If `true`, re-runs summarization for articles even when a summary already exists. |
| `briefingPrompt` | string | no | Custom instruction for the final synthesis step. If null/blank, uses `xai.briefing-prompt` from configuration, then the default from `prompts/news-analyze.yaml`. |

**Constraints**

- `startDate` must be on or before `endDate` (remember `endDate` is exclusive on articles).
- Span from `startDate` to `endDate` must be at most **7** calendar days (exclusive end semantics).
- Requires a configured xAI API key; otherwise the service responds with an error.

**Response** (`200`, `application/json`)

| Field | Type | Description |
|--------|------|-------------|
| `startDate` | string (date) | Same as request (resolved). |
| `endDate` | string (date) | Exclusive end of the article window used. |
| `timeZone` | string | Resolved IANA zone id. |
| `articleCount` | number | Articles considered in that window. |
| `summariesFilledThisRun` | number | Summaries written or updated during **this** HTTP request only (not stored on the archived document). |
| `briefing` | string | Final briefing text. |

Typical client errors: `400` (validation), `502` / `503` (upstream xAI or missing key). See application logs for details.

**Example**

```http
POST /api/news/analyze HTTP/1.1
Content-Type: application/json

{
  "startDate": "2026-04-12",
  "endDate": "2026-04-13",
  "timeZone": "Asia/Hong_Kong"
}
```

---

## Read archived briefings

Archived rows are stored in MongoDB collection `news_daily_briefings`. Each document is keyed uniquely by `(timeZone, startDate, endDate)` matching the analyze window. Cron and API both write through the same upsert; the **latest** successful run wins for that key.

### Archived document shape

Fields returned in list and single-resource responses (camelCase in JSON):

| Field | Type | Description |
|--------|------|-------------|
| `id` | string | MongoDB document id. |
| `startDate` | string (date) | Window start (same semantics as analyze). |
| `endDate` | string (date) | Window exclusive end (same as analyze `endDate`). |
| `timeZone` | string | IANA zone id. |
| `articleCount` | number | Article count from the run that last wrote this row. |
| `briefing` | string | Stored briefing text. |
| `lastSource` | string | `CRON` or `API` — who performed the last upsert. |
| `createdAt` | string (date-time) | When this window was first archived. |
| `updatedAt` | string (date-time) | When this row was last updated. |

---

### `GET /api/news/briefings`

Paged list of archived briefings, default **newest `updatedAt` first**.

**Query parameters**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `timeZone` | no | Filter by IANA id (trimmed). If empty after trim, treated as absent. |
| `from` | no* | Inclusive lower bound on stored **`startDate`**. |
| `to` | no* | Inclusive upper bound on stored **`startDate`**. |

\* If either `from` or `to` is present, **both** must be present. If `from` is after `to`, the server responds with `400`.

**Pagination** (Spring Data)

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Zero-based page index. |
| `size` | `20` | Page size. |
| `sort` | `updatedAt,desc` | Sort; property names are entity fields (e.g. `startDate`, `updatedAt`). |

**Response** (`200`, `application/json`)

Wrapper object:

| Field | Type | Description |
|--------|------|-------------|
| `items` | array | `NewsDailyBriefing` objects (see above). |
| `page` | number | Current page index. |
| `size` | number | Page size. |
| `totalElements` | number | Total matching documents. |
| `totalPages` | number | Total pages. |
| `first` | boolean | Whether this is the first page. |
| `last` | boolean | Whether this is the last page. |

---

### `GET /api/news/briefings/window`

Returns **one** archived briefing for the exact upsert key.

**Query parameters** (all required)

| Parameter | Description |
|-----------|-------------|
| `timeZone` | IANA zone id (must be non-blank after trim). |
| `startDate` | ISO date. |
| `endDate` | ISO date (exclusive end of article window, same as analyze). |

**Responses**

- `200` — single archived document.
- `400` — blank `timeZone`.
- `404` — no row for that triple.

**Example**

```http
GET /api/news/briefings/window?timeZone=Asia%2FHong_Kong&startDate=2026-04-12&endDate=2026-04-13
```

---

### `GET /api/news/briefings/{id}`

Returns one archived briefing by MongoDB **`id`**.

**Responses**

- `200` — document body.
- `404` — unknown id.

---

## Persistence

- **Unique key:** `(timeZone, startDate, endDate)` (enforced with a compound unique index).
- **Upsert:** Each successful `POST /api/news/analyze` and each successful scheduled daily job update the same logical row when the triple matches, refreshing `briefing`, `articleCount`, `updatedAt`, and `lastSource`, and preserving `createdAt` on first insert only.
- **Scheduled job:** When `news.daily-briefing.enabled` is true, the scheduler runs analyze in the configured zone and upserts with `lastSource = CRON`. The window is `[anchorDate, anchorDate + N)` where `anchorDate` is `today` in `news.daily-briefing.zone-id` minus `news.daily-briefing.day-offset` (env `NEWS_DAILY_BRIEFING_DAY_OFFSET`, default `7`), and `N` is `news.daily-briefing.window-days` (env `NEWS_DAILY_BRIEFING_WINDOW_DAYS`, default `1`, clamped to `1..7`). Use the same `startDate`/`endDate` in `GET .../window` to fetch that row.

---

## Related configuration

- xAI: `application.properties` / environment (API key, model, batch size, optional default `briefing-prompt`).
- Default synthesis text: `my-app/src/main/resources/prompts/news-analyze.yaml`.
- Daily briefing schedule: `news.daily-briefing.*` in `application.properties`.
