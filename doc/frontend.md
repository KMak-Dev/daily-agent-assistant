# Frontend overview (`briefing-ui`)

The frontend is a **single-page application** in **`briefing-ui/`** built with **React 19**, **TypeScript**, and **Vite 8**. It talks to the Spring Boot API over relative **`/api/...`** URLs (Vite dev proxy in development).

**Layout:** a **fixed left sidebar** lists archived briefings whose window **`startDate`** falls in the **last 90 local calendar days** (inclusive), **newest `startDate` first**; the list **scrolls** inside the sidebar. The **right column** is a **main** region with a **top toolbar** and scrollable content below.

**Sections (no URL router):** toolbar tabs switch **`appView`** in **`App.tsx`** — **Briefing on assets** (default: news archive Markdown reader + overflow **⋯** actions), **Current positions** (stock holdings CRUD), and **News keywords** (World News ingest query terms). There is **no client-side router** (no React Router): one **`App`** tree, **`useState`** for the active section.

There is **no global state library** (no Redux, Zustand, etc.): local React state and **`fetch`** wrappers under **`src/api/`** suffice.

For briefing HTTP semantics (pagination, filters, analyze vs archive), see **[briefing-api.md](./briefing-api.md)**. For positions and other **`/api`** routes, see **[backend.md](./backend.md)**.

---

## Prerequisites

- **Node.js** with **npm** (versions pinned indirectly via `package-lock.json` if present; otherwise use current LTS).
- The **Spring Boot** app serving **`/api/...`** (default **`http://localhost:8080`** when using the dev proxy described below), including briefings, positions, and world-news keywords as used by the UI.

---

## Commands (from `briefing-ui/`)

| Command | Purpose |
|---------|---------|
| `npm install` | Install dependencies (first time or after lockfile changes). |
| `npm run dev` | Start Vite dev server (default **`http://localhost:5173`**); hot module replacement for React. |
| `npm run build` | Type-check (`tsc -b`) then production bundle to **`dist/`**. |
| `npm run preview` | Serve **`dist/`** locally to smoke-test the production build. |
| `npm run lint` | Run ESLint on the project. |

From the **repository root**, use `cd briefing-ui && npm run dev` (etc.).

---

## Project layout

| Path | Role |
|------|------|
| **`index.html`** | HTML shell: `div#root`, page title **“Daily briefings”**, favicon. |
| **`vite.config.ts`** | Vite + `@vitejs/plugin-react`; **dev proxy** for `/api` → backend. |
| **`package.json`** | Scripts and dependencies (`react`, `react-dom`, `react-markdown`, …). |
| **`src/main.tsx`** | React 19 `createRoot`, **`StrictMode`**, mounts **`App`**. |
| **`src/index.css`** | Minimal global reset: full-height `html`, `body`, `#root`. |
| **`src/App.tsx`** | Shell: sidebar briefing list, toolbar section tabs, conditional main content (briefing reader, positions panel, keywords panel), briefing overflow menu (rerun / delete). |
| **`src/App.css`** | All application styles: layout, theme tokens, typography, briefing reader, positions/keywords shared “panel” styles, keywords preview block. |
| **`src/types.ts`** | TypeScript types for briefings, positions, world-news keywords, and analyze response. |
| **`src/api/briefings.ts`** | Briefings list, rerun analyze, delete archived briefing. |
| **`src/api/positions.ts`** | Stock positions list, bulk create, update by symbol, delete by symbol. |
| **`src/api/keywords.ts`** | World News keywords list, create, delete by id. |
| **`src/CurrentPositionsPanel.tsx`** | **Current positions** view: table + add/edit form (**`GET/POST/PUT/DELETE /api/positions`**). |
| **`src/NewsKeywordsPanel.tsx`** | **News keywords** view: table + add form, search-text preview (**`GET/POST/DELETE /api/world-news/keywords`**). |

There is **no** `src/components/` barrel (feature panels live beside **`App.tsx`**). There is **no** client-side router package.

---

## Runtime architecture

### Boot sequence

1. Browser loads **`index.html`** → **`src/main.tsx`** (ES module).
2. **`main.tsx`** renders `<StrictMode><App /></StrictMode>` into **`#root`**.
3. **`App.tsx`** mounts and its briefing **`useEffect`** (empty dependency array) runs **once**.
4. That effect calls **`fetchRecentBriefings()`**, which issues one or more **`GET /api/news/briefings`** requests with **`from`**, **`to`** (90-day inclusive range on **`startDate`**, using the browser’s local calendar), **`sort=startDate,desc`**, **`size=100`**, and **`page=0…`** until **`last`** is true (relative URLs).
5. On success, **`rows`** is set to the **concatenated** **`items`** (then client-sorted); **`selectedId`** is set to the **first item’s `id`** (or **`null`** if the list is empty).
6. The UI re-renders: sidebar always reflects that list; the main column depends on **`appView`**:
   - **`briefings`**: **“Select a briefing.”** if none selected, else Markdown reader for **`selected`**.
   - **`holdings`** / **`keywords`**: **`CurrentPositionsPanel`** / **`NewsKeywordsPanel`** each load their own data on **mount** (see below).

### Why the briefing `useEffect` runs once

The dependency array is **`[]`**, so the **briefings list** load runs on mount only. There is **no polling** and **no refetch** when selecting another row: each list row already includes the full **`briefing`** string from the API, so the reader is in-memory presentation until **rerun** or **delete** refreshes the list.

**After actions:** **Rerun analysis** and **Delete from archive** call the API, then **`fetchRecentBriefings()`** again and reconcile **`selectedId`** with the new list.

### Section panels (positions / keywords)

**`CurrentPositionsPanel`** and **`NewsKeywordsPanel`** are rendered only when their **`appView`** is active. Switching away **unmounts** the panel, so switching back **remounts** it; each runs a **fresh list** fetch on mount.

### Cancellation flag

The effect declares **`let cancelled = false`** and returns a cleanup that sets **`cancelled = true`**. After `await fetch`, every branch checks **`if (cancelled) return`** before calling **`setState`**. That avoids React 18 Strict Mode double-invocation (or fast unmount) causing **“set state on unmounted component”** warnings or stale updates.

### Selection model (briefings)

- **`selectedId`**: `string | null` — Mongo id of the selected **`NewsDailyBriefing`**.
- **`selected`**: derived with **`useMemo`** as **`rows.find(r => r.id === selectedId) ?? null`** so the reader always reads a consistent object when **`selectedId`** is non-null and present in **`rows`**.

Clicking a sidebar row calls **`setSelectedId(b.id)`**, **`setAppView('briefings')`**, and closes the overflow menu; the row gets the **`.active`** class when **`appView === 'briefings'`** and ids match.

### Toolbar and overflow actions

- **`TOOLBAR_NAV_ITEMS`** drives **Briefing on assets** / **Current positions** / **News keywords** links (**`aria-current="page"`** on the active tab).
- When **`appView === 'briefings'`** and **`selected`** is set, the trailing **⋯** menu offers **Rerun analysis** (**`POST /api/news/analyze`** with the row’s **`startDate`**, **`endDate`**, **`timeZone`**) and **Delete from archive** (**`DELETE /api/news/briefings/{id}`**). Success and error messages appear in a thin **banner** under the toolbar.
- **`appView !== 'briefings'`** clears the menu and ok-banner (errors are per-panel inside **`CurrentPositionsPanel`** / **`NewsKeywordsPanel`**).

### Briefing Markdown layout

**`splitBriefingOpening`** splits **`briefing`** on the **first blank line** (paragraph break): the **opening** block is rendered in **`.briefing-headline`** (lead typography); the remainder in **`.briefing-article-body`**. Meta under the headline shows **`formatBriefingUpdatedAtInZone(updatedAt, timeZone)`** and article count.

### Date window formatting

**`formatBriefingWindowDisplay(b)`** in **`App.tsx`**:

- The API stores **`endDate` as an exclusive** bound on the article window (see **`doc/briefing-api.md`**). For labels only, the UI shows an **inclusive** last day: **`displayEnd = calendar day before `endDate``** (UTC date arithmetic on the ISO strings).
- If **`startDate === displayEnd`** (typical single-day window such as `[15, 16)`), returns a **single** ISO date.
- Otherwise returns **`${startDate} \u2013 ${displayEnd}`** — an **en dash** (U+2013) with spaces, not ASCII hyphen-minus.
- If **`endDate`** is not a valid **`YYYY-MM-DD`** string, or **`displayEnd < startDate`**, the UI falls back to the legacy pattern (raw **`startDate`** / **`endDate`**).

This string is used in the **sidebar primary label** and in the **`aria-label`** on **`<main>`** when a briefing is selected. **Rerun** and other API calls still send the stored **`startDate`** / **`endDate`** unchanged.

---

## API integration

### Endpoint

The client calls:

```http
GET /api/news/briefings?page=0&size=100&sort=startDate,desc&from=YYYY-MM-DD&to=YYYY-MM-DD
Accept: application/json
```

**Implementation:** **`src/api/briefings.ts`** — **`BRIEFING_SIDEBAR_RECENT_DAYS`** (90), **`briefingSidebarDateRange()`**, and paged fetches until **`last`**.

### Sort order

The backend’s **`NewsBriefingController.list`** still defaults to **`updatedAt,desc`** when no `sort` is sent, but the client **passes `sort=startDate,desc`**. **`fetchRecentBriefings`** applies a **stable client reorder** (same primary order; ties broken by **`endDate`**, then **`timeZone`**) so the sidebar order is deterministic if the server omits secondary keys.

### Response shape

The JSON is parsed as **`NewsBriefingPageResponse`** (`src/types.ts`):

| Field | Type | Used by UI |
|-------|------|------------|
| **`items`** | `NewsDailyBriefing[]` | Yes — entire sidebar and main content. |
| **`page`**, **`size`**, **`totalElements`**, **`totalPages`**, **`first`**, **`last`** | pagination metadata | **`last`** drives multi-page loading; other fields unused in the UI today. |

Each **`NewsDailyBriefing`** mirrors the Spring document / Jackson serialization (camelCase):

| Field | Type | UI usage |
|-------|------|----------|
| **`id`** | string | React **`key`**, selection id. |
| **`startDate`**, **`endDate`** | string (`YYYY-MM-DD`) | **`formatBriefingWindowDisplay`** (display only; **`endDate`** still exclusive for API), **`aria-label`**. |
| **`timeZone`** | string | Secondary line in list; **`aria-label`**. |
| **`articleCount`** | number | Sidebar third line; main article **“N articles read”** line. |
| **`briefing`** | string (Markdown/plain) | **`ReactMarkdown`** source. |
| **`lastSource`** | optional string | Appended to **`aria-label`** when present. |
| **`createdAt`**, **`updatedAt`** | string (ISO instant) | **`updatedAt`** in main article meta: wall time in **`timeZone`** (**`formatBriefingUpdatedAtInZone`**). **`createdAt`** not shown. |

### Error handling

- **`fetch`** non-OK: throws **`Error`** with response **body text** or a fallback **`Request failed (status)`**.
- Invalid JSON shape: if **`items`** is not an array, throws **`Invalid response: missing items array`**.
- **`App`** catches and sets **`error`** string for display in the sidebar **`state-block.error`**.

### Stock positions (`src/api/positions.ts`, `CurrentPositionsPanel`)

| Call | HTTP | Purpose |
|------|------|---------|
| **`fetchPositions`** | **`GET /api/positions`** | Load all rows for the table. |
| **`createPositions`** | **`POST /api/positions`** | Body: JSON **array** of **`{ symbol, quantity, opened_at }`** (UI sends one element for “Add”). |
| **`updatePosition`** | **`PUT /api/positions/{symbol}`** | Patch quantity and/or opened time. |
| **`deletePositionBySymbol`** | **`DELETE /api/positions?symbol=…`** | Remove one symbol. |

Types: **`StockPosition`** in **`types.ts`** (**`openedAt`**, **`lastUpdated`** — camelCase in TS; wire format uses snake_case where the API requires it in bodies only).

### World News keywords (`src/api/keywords.ts`, `NewsKeywordsPanel`)

| Call | HTTP | Purpose |
|------|------|---------|
| **`fetchKeywords`** | **`GET /api/world-news/keywords`** | Ordered list (**`sort_order`**, then **`id`** on the server). |
| **`createKeyword`** | **`POST /api/world-news/keywords`** | JSON **`{ keyword, operator, sort_order? }`** — **`operator`** is **`OR`** or **`NOT`**; **`sort_order`** optional (server defaults to **`0`**). |
| **`deleteKeywordById`** | **`DELETE /api/world-news/keywords/{id}`** | **`204`** on success. |

The panel shows a **search text preview** built client-side with the same rules as the backend query builder (OR joins positives; NOT appends **`-term`**). Types: **`WorldNewsKeyword`**, **`WorldNewsKeywordOperator`** in **`types.ts`** (**`sort_order`** matches Jackson **`@JsonProperty("sort_order")`** on list/create responses).

### Briefing rerun and delete (`src/api/briefings.ts`)

| Call | HTTP | Purpose |
|------|------|---------|
| **`rerunBriefingAnalysis`** | **`POST /api/news/analyze`** | Body: **`startDate`**, **`endDate`**, **`timeZone`** from the selected archived row; returns **`NewsAnalyzeResponse`**. |
| **`deleteArchivedBriefing`** | **`DELETE /api/news/briefings/{id}`** | Removes one archive document by Mongo **`id`**. |

---

## Development proxy and CORS

### Vite proxy

**`vite.config.ts`** defines:

```ts
server: {
  proxy: {
    '/api': {
      target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
      changeOrigin: true,
    },
  },
},
```

During **`npm run dev`**, the browser talks to the **Vite origin** (e.g. port **5173**). Requests to **`/api/...`** are forwarded to the Spring app, so the browser does not need **CORS** to be enabled on the backend for that setup.

### Overriding the backend URL

Set environment variable **`VITE_API_PROXY_TARGET`** when starting Vite, e.g.:

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
```

Useful when the API is on another host/port or behind **Docker** (e.g. published on **`localhost:8080`** while Vite runs on the host).

### Production builds and Docker

**`npm run build`** emits static files under **`briefing-ui/dist/`**.

**Docker Compose (recommended full stack):** the **`web`** service builds **`briefing-ui/Dockerfile`** (Node build → **nginx**). Nginx serves **`dist/`** and proxies **`/api/`** to **`http://app:8080`** with long timeouts for analyze/rerun. Open **`http://localhost`** (default port **80**, override with **`WEB_PORT`** in `.env`). No frontend env vars are required: the browser keeps relative **`/api`** URLs on the same origin.

**Local production smoke test:** `npm run preview` does **not** proxy **`/api`**; use Compose **`web`** or configure your own reverse proxy.

If the SPA is hosted on a **different origin** than the API, you must enable **CORS** on Spring or use a proxy — the repo does not add CORS by default (see **backend.md**).

---

## UI structure (DOM and roles)

### Root: **`.app-shell`**

A **flex row** filling the viewport height (**`height: 100%`** with **`min-height: 0`** on flex children to allow internal scrolling). Background **`--bg`**.

### Left: **`<aside class="sidebar">`**

- **`aria-labelledby="sidebar-heading"`** tied to **`h1#sidebar-heading`**.
- **Header** (`.sidebar-header`): title **“Recent briefings”**, subtitle **“Latest briefings from last 7 days”**
- **List region** (`.list-scroll`, **`role="list"`**): contains either loading/error/empty **`div.state-block`** or a list of **`button.date-row`** elements with **`role="listitem"`**.

Each row is a **`<button type="button">`** (keyboard and screen-reader friendly) with three lines:

- **`.date-row-title`**: **`Briefing on {inclusiveLastDay}`** where **`inclusiveLastDay`** is the last calendar day covered (exclusive API **`endDate`** converted for display; see **`inclusiveCoverageEndDate`** in **`App.tsx`**).
- **`.date-row-range`**: **`formatBriefingWindowDisplay(b)`** — inclusive window (single ISO date or **`start \u2013 inclusiveEnd`** with an en dash).
- **`.date-row-meta`**: **`{timeZone} · {articleCount} articles`**.

### Toolbar shelf: **`.main-toolbar-shelf`**

Inside **`.main-measure`**: **`nav.main-toolbar-nav`** (section tabs) and **`div.main-toolbar-trailing`** (placeholder width for the **⋯** slot so layout does not shift; the trigger is visible only on **Briefing on assets** with a selection). A **`.main-action-banner`** may appear under the inner toolbar row after rerun/delete.

Scrolling the reader adds **`.main-toolbar-shelf--scrolled`** for a light shadow ( **`onScroll`** on **`.main-reader`**).

### Right: **`<main class="main">`**

- **`aria-label`** on **`main`** reflects the active section (briefing window summary when **`briefings`** + **`selected`**, or **“Current positions”** / **“News keywords”**).
- **`appView === 'briefings'`**:
  - **Empty selection** (e.g. no rows): **`state-block`** “Select a briefing.”
  - **With selection**: **`.briefing-body` > `article`**: **`.briefing-headline`** and **`.briefing-article-meta`**, then optional **`.briefing-article-body`**, each wrapping **`ReactMarkdown`** for the split parts of **`briefing`**.
- **`appView === 'holdings'`**: root **`.positions-panel`** from **`CurrentPositionsPanel`** (title, lead, banner, optional form, table or states).
- **`appView === 'keywords'`**: same panel primitives + **`.keywords-preview`** (label + **`<code>`** preview) from **`NewsKeywordsPanel`**.

### Markdown rendering (briefings only)

**`react-markdown`** renders each Markdown fragment as semantic HTML. There is **no** `remark-gfm` plugin in the current dependency set: GitHub-flavored extras (tables, task lists) depend on default CommonMark behavior unless you add plugins later.

**Empty briefing:** if **`briefing`** is null/blank after trim, the string **`_Empty briefing._`** is passed so Markdown shows light italic placeholder text.

---

## Styling and design tokens (`App.css`)

### Global tokens (`:root`)

| Token | Typical value | Role |
|-------|----------------|------|
| **`--bg`** | `#ffffff` | App shell background. |
| **`--panel`** | `#fafafa` | Main reading column background. |
| **`--border`** | `#e5e5e5` | Hairlines (sidebar edge, header rules). |
| **`--muted`** | `#525252` | Secondary labels (sidebar title, meta, states). |
| **`--text`** | `#0a0a0a` | Primary foreground (lead paragraph, links). |
| **`--accent`** | `#000000` | Active row left bar color. |
| **`--body-copy`** | `#262626` | Article body text. |
| **`--strong`** | `#333333` | **`strong` / bold** — slightly softer than **`--text`**. |
| **`--main-sidebar-gap`** | `24px` (tunable) | **`padding-left`** on **`.main`** so content is inset without a white “gutter” between flex items. |

**`color-scheme: light`** hints UA widgets toward light mode.

### Layout details

- **Sidebar width:** **`min(360px, 38vw)`** with **`min-width: 0`** so flex shrink behaves predictably on narrow viewports.
- **Sidebar background:** subtle vertical gradient white → light gray; **border-right** separates from main.
- **Main column:** **`flex: 1`**, **`padding-left: var(--main-sidebar-gap)`** — the inset uses **`--panel`** so there is no exposed **`--bg`** strip between panes (unlike using **`gap`** on the shell, which would show white between columns).
- **`.briefing-body`:** generous vertical padding; **`article`** uses **`max-width: none`** — horizontal reading width is constrained by the parent **`.main-measure`** (**`--reader-measure`** ≈ **70rem** plus **`--reader-inline-pad`**).

### Typography

- **System UI stack** on **`html`** via **`:root`**.
- **Sidebar `h1`**: small caps feel — uppercase, wide **`letter-spacing`**, **`--muted`** color.
- **Sidebar list rows**: **`.date-row-title`** uses **`--text`** and tight letter-spacing; **`.date-row-range`** and **`.date-row-meta`** step down in size and use **`--muted`** / **`--subtle`**.
- **Headline block** (`.briefing-headline`): first Markdown segment from **`splitBriefingOpening`** — **`p`** / **`h1`** use a larger **clamp** font, **`--text`**, tight letter-spacing.
- **Headings inside Markdown** are **not** custom-styled in CSS today: they follow browser defaults scaled by the article’s base font size.
- **`strong`**: **`--strong`** color and **`font-weight: 600`**. **`a strong`** uses **`color: inherit`** so bold inside links does not override link color.

### Interactive states

- **`.date-row:hover`**: light translucent gray overlay.
- **`.date-row.active`**: slightly stronger overlay + **2px** left border in **`--accent`**.
- **`.state-block.error`**: red text **`#b91c1c`** for API errors.
- **Positions / keywords panels** reuse **`.positions-*`** class names (buttons, form grid, table, banners, danger link buttons). **`.keywords-preview`** / **`.keywords-preview-code`** style the ingest query preview on the keywords page.

---

## Accessibility notes

- **Landmarks:** **`aside`** and **`main`** structure the page for screen readers.
- **Sidebar title:** **`h1#sidebar-heading`** is the single document-level heading in the chrome; **`aside`** references it with **`aria-labelledby`**.
- **List semantics:** **`role="list"`** / **`role="listitem"`** on buttons (native **`button`** ensures activation with Enter/Space).
- **Main context:** **`aria-label`** on **`main`** summarizes the active section (briefing window, zone, optional **`lastSource`** when **`briefings`** + selected, or the static labels for holdings / keywords).
- **Section tabs:** **`nav`** with **`aria-label="Sections"`**; active tab uses **`aria-current="page"`**.
- **Overflow menu:** trigger **`aria-haspopup`**, **`aria-expanded`**, menu **`role="menu"`** / items **`role="menuitem"`**.

---

## Security and content safety

**`react-markdown`** by default does **not** treat arbitrary HTML in the source as raw HTML unless configured with **`rehype-raw`** (not used). Briefing text is therefore rendered as **Markdown → safe DOM** without **`dangerouslySetInnerHTML`**.

Links in briefings render as normal **`<a href>`** elements with **`target` not forced** — they open in the same tab unless the Markdown includes HTML (again, not parsed as HTML here). If briefings ever contain untrusted URLs, consider adding **`rel="noopener noreferrer"`** via **`components`** prop on **`ReactMarkdown`** in a future change.

---

## Relationship to other “frontends”

The Spring Boot app may expose **`GET /`** with a minimal JSON status payload (**backend.md**); that is **not** this project. **`briefing-ui`** is a **separate** deployable static site intended to be built and hosted alongside or in front of the API.

---

## Troubleshooting

| Symptom | Likely cause | What to check |
|---------|----------------|----------------|
| Sidebar shows raw HTML error or “Failed to fetch” | Backend down, wrong proxy target, or network blocked. | Spring running on **`8080`**; **`VITE_API_PROXY_TARGET`**; browser devtools **Network** tab. |
| CORS errors in dev | Dev server not used; opening **`file://`** or wrong port without proxy. | Use **`npm run dev`** and relative **`/api`** URLs. |
| Empty list | No rows in **`news_daily_briefings`**, or Mongo/API misconfigured. | **`GET /api/news/briefings`** via curl; backend logs. |
| Headline block not “lead” styled | Opening segment is not a **`p`** or **`h1`** (e.g. starts with a list). | Styles target **`.briefing-headline p`** / **`.briefing-headline h1`**. |
| Bold looks wrong inside links | Should inherit link color. | **`.briefing-body article a strong`** rule in **`App.css`**. |
| Positions or keywords panel errors | Wrong **`/api`** path, backend 4xx/5xx, or validation. | Network tab for **`/api/positions`** or **`/api/world-news/keywords`**; panel shows **`positions-state--error`**. |
| Stale holdings after external change | Panel only refetches on mount. | Switch to another tab and back to remount **`CurrentPositionsPanel`**. |

---

## Future extensions (not implemented)

Documented here so contributors know the current boundaries:

- **Pagination / “load more”** beyond seven briefing rows in the sidebar.
- **Filters** (`timeZone`, `from`/`to`) supported by the briefings API but unused in UI.
- **`GET /api/news/briefings/{id}`** for detail-only fetch (smaller list payload).
- **Theming** (dark mode): would require new token sets and possibly **`prefers-color-scheme`**.
- **URL-based routing** / deep links (e.g. **`/holdings`**, **`/keywords`**) — today section state is not reflected in the URL.
- **Inline “Run analyze”** with a custom date window from the UI (only **rerun** for an existing archive window is wired).
- **Editing** world-news keyword rows in place (API today is list / create / delete only).

---

## Cross-references

| Document | Content |
|----------|---------|
| **[briefing-api.md](./briefing-api.md)** | `GET /api/news/briefings`, `POST /api/news/analyze`, persistence, examples. |
| **[backend.md](./backend.md)** | Spring Boot stack, Compose, ports, **`/api/positions`**, **`/api/world-news/keywords`**, and other routes. |
