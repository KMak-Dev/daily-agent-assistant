# How this repository’s frontend maps to React concepts

This document is the **bridge** between the generic **[react-tutorial.md](react-tutorial.md)** and the **actual code** under **`briefing-ui/`**. Read it with your editor open: it names files, state variables, and effects so you can click through the implementation.

**Companion docs (repo root):**

- **[doc/frontend.md](../doc/frontend.md)** — commands (`npm run dev`), directory layout, boot sequence, selection model, date formatting, proxy behavior.
- **[doc/briefing-api.md](../doc/briefing-api.md)** — pagination, analyze vs archive, request/response shapes the UI relies on.

**Backend concepts (Java / Spring):** If you are learning the server side in parallel, use **[java-tutorial-for-beginners.md](java-tutorial-for-beginners.md)**, **[spring-boot-tutorial.md](spring-boot-tutorial.md)**, and the concept map in **[backend-concepts.md](backend-concepts.md)** for **`my-app/`**.

---

## 1. Entry point: `createRoot` and `StrictMode`

| Concept (react-tutorial) | Where it is |
|--------------------------|-------------|
| Client rendering API | **`briefing-ui/src/main.tsx`** — `createRoot(document.getElementById('root')!).render(…)` |
| Development checks | **`<StrictMode>`** wraps **`<App />`** in the same file |

**Why it matters here:** Strict Mode’s development behavior interacts with the initial **`fetch`** in `App`—the code uses a **`cancelled`** flag in the load effect so async completion does not call **`setState`** after teardown (see **§3** and **[doc/frontend.md](../doc/frontend.md)** “Cancellation flag”).

---

## 2. Single component tree (no router)

| Concept | In this repo |
|---------|----------------|
| One-screen SPA | **[doc/frontend.md](../doc/frontend.md)** states there is **no client-side router**; **`App`** is the whole UI. |
| Root component | **`briefing-ui/src/App.tsx`** — default export **`App`** |

Everything visible is either **`App`** or something **`App`** renders (including **`react-markdown`** output).

---

## 3. State: what the UI remembers

`App` uses **`useState`** for UI-owned data. This mirrors the “state vs props” discussion in **[react-tutorial.md](react-tutorial.md)** Part 3.

| State | Role |
|-------|------|
| `rows` | List of **`NewsDailyBriefing`** from **`GET /api/news/briefings`** (sidebar + in-memory detail source). |
| `selectedId` | Which briefing is active in the two-pane layout. |
| `loading` / `error` | First-load status for the list fetch. |
| `actionsMenuOpen` | Overflow “⋯” menu visibility. |
| `overflowBusy` | Disables actions while **rerun** or **delete** is in flight. |
| `actionBanner` | Short-lived success/error message after actions. |

**Derived state (not stored separately):**

- **`selected`** — `useMemo` + `rows.find(…)` so the main column always reads the row matching **`selectedId`** ([react-tutorial.md](react-tutorial.md) Part 6, and **[doc/frontend.md](../doc/frontend.md)** “Selection model”).
- **`briefingParts`** — `useMemo` splitting Markdown into “opening” vs body for layout; depends on **`selected`**.

---

## 4. Effects: three different jobs in one component

### 4.1 Initial data load (`[]`)

| Concept | Implementation |
|---------|----------------|
| `useEffect` + empty deps | **`App.tsx`** — loads **`fetchRecentBriefings()`** once on mount. |
| Async pattern | Inner **`async` IIFE**; not `async useEffect`. |
| Cancellation | **`let cancelled = false`** + cleanup sets **`true`**; every **`setState`** after **`await`** checks **`if (cancelled) return`**. |

The API wrapper lives in **`briefing-ui/src/api/briefings.ts`** (`fetchRecentBriefings`). That keeps **`App`** focused on UI orchestration ([react-tutorial.md](react-tutorial.md) Part 10).

### 4.2 Auto-dismiss banner (`[actionBanner]`)

When **`actionBanner`** is a success kind, an effect starts a **timeout** to clear it; cleanup clears the timeout. This is the **`useEffect` cleanup** pattern from **[react-tutorial.md](react-tutorial.md)** Part 4 applied to **transient UX**.

### 4.3 Click-outside and Escape for the menu (`[actionsMenuOpen]`)

When the menu opens, an effect registers **`pointerdown`** and **`keydown`** listeners on **`document`**; cleanup removes them. **`useRef`** (`actionsWrapRef`) holds the wrapper DOM node so **outside** clicks close the menu without closing on **inside** clicks ([react-tutorial.md](react-tutorial.md) Part 7).

---

## 5. Events: user actions → async handlers

| User action | Handler | Network |
|-------------|---------|---------|
| Sidebar row click | `setSelectedId` + closes menu | None (data already in **`rows`**). |
| Rerun analysis | **`onRerunAnalysis`** | **`POST /api/news/analyze`** via **`rerunBriefingAnalysis`**, then **`fetchRecentBriefings`** to refresh list. |
| Delete from archive | **`onDeleteFromArchive`** (after **`confirm`**) | **`DELETE /api/news/briefings/{id}`** via **`deleteArchivedBriefing`**, then refetch. |

Types for JSON bodies and list pages are centralized in **`briefing-ui/src/types.ts`**—TypeScript interfaces mirroring the API ([react-tutorial.md](react-tutorial.md) Part 1, TypeScript note).

---

## 6. Lists and keys

Sidebar buttons **`map`** over **`rows`**. Each button uses **`key={b.id}`** (stable server id)—see **[react-tutorial.md](react-tutorial.md)** Part 8. The active row adds a CSS class when **`b.id === selectedId`**.

---

## 7. Markdown as a child component

| Library | Usage |
|---------|--------|
| **`react-markdown`** | **`ReactMarkdown`** renders **`briefingParts.opening`** and **`briefingParts.rest`** inside **`article`** ([react-tutorial.md](react-tutorial.md) Part 12). |

Styling for the reader lives in **`briefing-ui/src/App.css`** (imported from **`App.tsx`**).

---

## 8. Vite proxy and “same-origin” fetches

The tutorial explains **`fetch('/api/...')`** relative URLs ([react-tutorial.md](react-tutorial.md) Part 13). Here, **`vite.config.ts`** proxies **`/api`** to the Spring Boot app (**`VITE_API_PROXY_TARGET`** or **`http://localhost:8080`**), so the browser talks to the Vite origin and avoids casual CORS issues in development.

**[doc/frontend.md](../doc/frontend.md)** documents the same behavior in product terms.

---

## 9. Date and window display (pure functions, not React-specific)

**`formatBriefingWindowDisplay`**, **`exclusiveEndToInclusiveLastDay`**, **`formatBriefingUpdatedAtInZone`**, and **`inclusiveCoverageEndDate`** in **`App.tsx`** are **ordinary functions** used during render. They implement the **exclusive `endDate`** contract from the API (**[doc/briefing-api.md](../doc/briefing-api.md)**) for human-readable labels—no extra React APIs beyond calling them from JSX.

---

## 10. Suggested reading order (hands-on)

1. **[react-tutorial.md](react-tutorial.md)** — hooks and mental model.
2. **`briefing-ui/src/main.tsx`** → **`App.tsx`** (top to bottom: state → effects → return).
3. **`briefing-ui/src/api/briefings.ts`** + **`types.ts`**.
4. **`briefing-ui/vite.config.ts`** + **`doc/frontend.md`**.
5. **`doc/briefing-api.md`** when you change request parameters or response handling.

If you change the UI, run **`npm run lint`** and **`npm run build`** from **`briefing-ui/`** as described in **[doc/frontend.md](../doc/frontend.md)**.
