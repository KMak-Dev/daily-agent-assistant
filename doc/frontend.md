# Frontend overview (`briefing-ui`)

The frontend is a **single-page application** in **`briefing-ui/`** built with **React 19**, **TypeScript**, and **Vite 8**. It reads **archived daily briefings** from the Spring Boot API and presents a **two-pane layout**: a scrollable list on the left (**Latest briefings from last 7 days** in the sidebar; up to **seven** rows, sorted by window **`startDate`**, newest first), and the **Markdown-rendered briefing text** on the right.

There is **no client-side router**: one screen, one `App` component tree. There is **no global state library** (no Redux, Zustand, etc.): local React state and one `fetch` on mount suffice.

For briefing HTTP semantics (pagination, filters, analyze vs archive), see **[briefing-api.md](./briefing-api.md)**. For the rest of the backend, see **[backend.md](./backend.md)**.

---

## Prerequisites

- **Node.js** with **npm** (versions pinned indirectly via `package-lock.json` if present; otherwise use current LTS).
- The **Spring Boot** app serving **`GET /api/news/briefings`** (default **`http://localhost:8080`** when using the dev proxy described below).

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
| **`src/App.tsx`** | Entire UI: data load, selection, sidebar, main column, Markdown. |
| **`src/App.css`** | All application styles: layout, theme tokens, typography, states. |
| **`src/types.ts`** | TypeScript interfaces mirroring JSON shapes from the API. |
| **`src/api/briefings.ts`** | `fetch` wrapper for the briefings list endpoint. |

There are no route modules, no `src/components/` split (by design: the surface area is small).

---

## Runtime architecture

### Boot sequence

1. Browser loads **`index.html`** → **`src/main.tsx`** (ES module).
2. **`main.tsx`** renders `<StrictMode><App /></StrictMode>` into **`#root`**.
3. **`App.tsx`** mounts and its **`useEffect`** (empty dependency array) runs **once**.
4. That effect calls **`fetchRecentBriefings()`**, which issues **`GET /api/news/briefings?page=0&size=7&sort=startDate,desc`** (relative URL).
5. On success, **`rows`** is set to the returned **`items`** array; **`selectedId`** is set to the **first item’s `id`** (or **`null`** if the list is empty).
6. The UI re-renders: sidebar shows rows or a **loading / error / empty** state; main shows either **“Select a briefing.”** or the **Markdown** for the selected row.

### Why `useEffect` runs once

The dependency array is **`[]`**, so the load runs on mount only. There is **no polling** and **no refetch** when selecting another row: each list row already includes the full **`briefing`** string from the API, so the right pane is pure presentation of in-memory data.

### Cancellation flag

The effect declares **`let cancelled = false`** and returns a cleanup that sets **`cancelled = true`**. After `await fetch`, every branch checks **`if (cancelled) return`** before calling **`setState`**. That avoids React 18 Strict Mode double-invocation (or fast unmount) causing **“set state on unmounted component”** warnings or stale updates.

### Selection model

- **`selectedId`**: `string | null` — Mongo id of the selected **`NewsDailyBriefing`**.
- **`selected`**: derived with **`useMemo`** as **`rows.find(r => r.id === selectedId) ?? null`** so the main column always reads a consistent object when **`selectedId`** is non-null and present in **`rows`**.

Clicking a sidebar button calls **`setSelectedId(b.id)`** and toggles the **`.active`** class for styling.

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
GET /api/news/briefings?page=0&size=7&sort=startDate,desc
Accept: application/json
```

**Implementation:** **`src/api/briefings.ts`**, constant **`SIDEBAR_LIMIT = 7`**.

### Sort order

The backend’s **`NewsBriefingController.list`** still defaults to **`updatedAt,desc`** when no `sort` is sent, but the client **passes `sort=startDate,desc`** so the first page is **seven briefings with the latest window `startDate` first**. **`fetchRecentBriefings`** also applies a **stable client reorder** (same primary order; ties broken by **`endDate`**, then **`timeZone`**) so the sidebar order is deterministic if the server omits secondary keys.

### Response shape

The JSON is parsed as **`NewsBriefingPageResponse`** (`src/types.ts`):

| Field | Type | Used by UI |
|-------|------|------------|
| **`items`** | `NewsDailyBriefing[]` | Yes — entire sidebar and main content. |
| **`page`**, **`size`**, **`totalElements`**, **`totalPages`**, **`first`**, **`last`** | pagination metadata | No — ignored today; only first page is fetched. |

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

### Production builds

**`npm run build`** emits static files under **`briefing-ui/dist/`**. There is **no proxy** in production unless you serve the app behind a reverse proxy that maps **`/api`** to the backend. Typical patterns:

- Same host: nginx routes **`/api`** → Spring, **`/`** → static **`dist/`**.
- Or configure Spring to serve **`dist/`** as static resources and keep API on the same origin.

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

### Right: **`<main class="main">`**

- When **`selected`** is set, **`aria-label`** summarizes the briefing for assistive tech (includes **`formatBriefingWindowDisplay`**, **`timeZone`**, optional **`lastSource`**). There is **no visible chrome header** above the article: context is implied by the list selection and the **`aria-label`**.
- **Empty selection** (e.g. no rows): **`state-block`** “Select a briefing.”
- **Content**: **`.briefing-body` > `article` > `ReactMarkdown`**.

### Markdown rendering

**`react-markdown`** renders the **`briefing`** string as semantic HTML (paragraphs, lists, headings, links, **`strong`**, etc.). There is **no** `remark-gfm` plugin in the current dependency set: GitHub-flavored extras (tables, task lists) depend on default CommonMark behavior unless you add plugins later.

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
- **`.briefing-body`:** scrollable region with generous padding; **`article`** max-width **52rem** for readable line length.

### Typography

- **System UI stack** on **`html`** via **`:root`**.
- **Sidebar `h1`**: small caps feel — uppercase, wide **`letter-spacing`**, **`--muted`** color.
- **Sidebar list rows**: **`.date-row-title`** uses **`--text`** and tight letter-spacing; **`.date-row-range`** and **`.date-row-meta`** step down in size and use **`--muted`** / **`--subtle`**.
- **First paragraph of article** (`.briefing-body article > p:first-child`): larger **clamp** font, sans-serif, **`--text`**, tight letter-spacing — treats the opening line as a **lead** when the model emits it as the first Markdown paragraph.
- **Headings inside Markdown** are **not** custom-styled in CSS today: they follow browser defaults scaled by the article’s base font size.
- **`strong`**: **`--strong`** color and **`font-weight: 600`**. **`a strong`** uses **`color: inherit`** so bold inside links does not override link color.

### Interactive states

- **`.date-row:hover`**: light translucent gray overlay.
- **`.date-row.active`**: slightly stronger overlay + **2px** left border in **`--accent`**.
- **`.state-block.error`**: red text **`#b91c1c`** for API errors.

---

## Accessibility notes

- **Landmarks:** **`aside`** and **`main`** structure the page for screen readers.
- **Sidebar title:** **`h1#sidebar-heading`** is the single document-level heading in the chrome; **`aside`** references it with **`aria-labelledby`**.
- **List semantics:** **`role="list"`** / **`role="listitem"`** on buttons (native **`button`** ensures activation with Enter/Space).
- **Main context:** when a briefing is open, **`aria-label`** on **`main`** conveys date window, zone, and source without duplicating that text visually above the article.

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
| First paragraph not “lead” styled | First Markdown node is not a **`p`** (e.g. starts with **`#`** or a list). | CSS targets **`article > p:first-child` only**. |
| Bold looks wrong inside links | Should inherit link color. | **`.briefing-body article a strong`** rule in **`App.css`**. |

---

## Future extensions (not implemented)

Documented here so contributors know the current boundaries:

- **Pagination / “load more”** beyond seven rows.
- **Filters** (`timeZone`, `from`/`to`) supported by the API but unused in UI.
- **`GET /api/news/briefings/{id}`** for detail-only fetch (smaller list payload).
- **Theming** (dark mode): would require new token sets and possibly **`prefers-color-scheme`**.
- **Router** for multiple views (settings, analyze trigger, etc.).

---

## Cross-references

| Document | Content |
|----------|---------|
| **[briefing-api.md](./briefing-api.md)** | `GET /api/news/briefings`, `POST /api/news/analyze`, persistence, examples. |
| **[backend.md](./backend.md)** | Spring Boot stack, Compose, ports, other `/api` routes. |
