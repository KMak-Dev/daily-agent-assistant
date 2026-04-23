# React tutorial (concepts before frameworks)

This guide teaches **core React** so you can read tutorials, docs, and production code (including this repository’s **`briefing-ui/`** app) with confidence. It assumes you know **JavaScript** reasonably well (functions, `async`/`await`, modules, destructuring, arrow functions). If you are stronger in **Java** than JS, skim [Java tutorial for beginners](java-tutorial-for-beginners.md) only for the *mindset* of static types—here we use **TypeScript** in examples where types clarify intent.

**Version note:** Examples match **React 18+ / 19** mental models (this repo uses **React 19** with **Vite 8**). Older blog posts may show **class components**; modern codebases use **function components** and **hooks** exclusively. This document follows that modern style.

**How to use this doc:** Read in order the first time. Later, use headings as a reference. Small runnable snippets are intentionally minimal so you can type them in a fresh Vite + React + TypeScript project if you want practice.

**After concepts:** To see the same ideas **mapped file-by-file** to this repository’s frontend, read **[react-in-this-repo.md](react-in-this-repo.md)**. Product-oriented behavior (commands, API URLs, layout) is summarized in **[doc/frontend.md](../doc/frontend.md)** at the repo root.

---

## Part 1 — React in context

### What problem React solves

Browsers expose the **DOM** (Document Object Model): a tree of nodes you can create, update, and delete with imperative APIs (`document.createElement`, `element.appendChild`, …). Building a rich UI by hand becomes **hard to reason about** when many events and async responses can change the same subtree.

**React** is a library for building **user interfaces** from **declarative descriptions**: you return a **tree of elements** that *should* match the current application state; React reconciles that tree with the previous one and updates the DOM **efficiently**. You think in **“what should be on screen given state X?”**, not “step 37: mutate this one span’s text.”

### Single-page applications (SPAs)

In an SPA, the server typically sends **one HTML shell** plus JavaScript bundles. Subsequent “navigation” may be handled client-side (routers) or, in small apps, **without a router**—one root component that swaps subtrees based on state. This repo’s UI is the latter: one screen, one `App` tree (see **[doc/frontend.md](../doc/frontend.md)**).

### JavaScript vs TypeScript in React projects

**JavaScript** runs in the browser as-is (after bundling). **TypeScript** adds **static types** that compile away; it catches many mistakes before runtime and makes large components easier to refactor. The **`briefing-ui/`** module is TypeScript-first (`*.tsx` files).

---

## Part 2 — Elements, JSX, and components

### Elements are plain descriptions (not DOM nodes)

At runtime, **`React.createElement`** (or the JSX compiler’s equivalent) produces a **plain object**: type (string tag name or function component), props, children. That object is **cheap**; creating new trees each render is normal.

### JSX syntax

JSX looks like HTML inside JavaScript. It must return **one root** (or use a **fragment** `<>...</>`).

```tsx
function Welcome(props: { name: string }) {
  return <h1>Hello, {props.name}</h1>
}
```

**Rules that trip beginners:**

- **`className`** instead of HTML’s `class` (JSX is JS; `class` is reserved).
- **camelCase** attributes: `onClick`, `aria-label` stays hyphenated as in DOM.
- **All tags must close**: `<img />`, `<br />`, not bare `<br>` in older TS configs.

### Function components

A **function component** is a function that receives **props** and returns React **nodes**. There are no “instances” like Java objects; React **calls your function** when it needs to render that part of the tree.

```tsx
type CounterProps = { initial: number }

function Counter({ initial }: CounterProps) {
  // hooks would go here (see later)
  return <output>{initial}</output>
}
```

---

## Part 3 — State, renders, and one-way data flow

### What “state” means

**State** is data that **belongs to the UI** and may change over time (loading flags, form fields, selected item). When state updates, React **schedules a re-render** for the affected components.

### `useState`

`useState` returns a **pair**: current value and a **setter**. Calling the setter notifies React to re-render.

```tsx
import { useState } from 'react'

function Toggle() {
  const [on, setOn] = useState(false)
  return (
    <button type="button" onClick={() => setOn((v) => !v)}>
      {on ? 'On' : 'Off'}
    </button>
  )
}
```

**Important:** Treat state values as **immutable**. For objects/arrays, replace them with **new** references (copy, spread, `map`, `filter`) instead of mutating fields in place. That keeps change detection predictable.

### One-way data flow

Data flows **down** via props. Events flow **up** via callbacks (`onClick`, custom `onSelect` props). Parent state + child props is the default pattern; avoid children writing to parent state without an explicit callback—**lifting state up** keeps a single source of truth.

---

## Part 4 — Effects: `useEffect`

### Effects vs render

**Render** must be **pure** with respect to React’s rules: given props and state, return UI—no unrelated side effects (no `fetch` without care, no timers, no direct global subscriptions) *inside* the render body itself.

**Effects** run **after** paint (by default) and express **synchronization** with the outside world: network, DOM APIs not exposed as props, timers, subscriptions.

```tsx
import { useEffect, useState } from 'react'

function Clock() {
  const [now, setNow] = useState(() => new Date().toISOString())

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date().toISOString()), 1000)
    return () => window.clearInterval(id)
  }, [])

  return <time dateTime={now}>{now}</time>
}
```

### Dependency array

The second argument to `useEffect` is the **dependency array**:

- **`[]`** — run **once** after mount (and cleanup on unmount). Use when the effect truly depends on nothing that changes, or you intentionally snapshot initial values (advanced).
- **`[a, b]`** — run after mount and whenever **`a` or `b` changes** (by `Object.is` comparison).
- **Omitted** (not shown in modern codebases often) — runs after **every** render; easy to cause loops if you also `setState` without care.

### Cleanup function

Returning a function from `useEffect` registers **cleanup**. React runs cleanup before re-running the effect (when deps change) and on unmount. Use it to cancel timers, remove listeners, abort network (with `AbortController`), etc.

---

## Part 5 — Strict Mode and “double mount” in development

**`<StrictMode>`** (see **`briefing-ui/src/main.tsx`**) enables extra checks in development. One visible behavior: React may **mount, unmount, and remount** components to surface unsafe side effects.

That matters for **`useEffect` with `[]`**: async work started on first mount might complete **after** the cleanup ran. If you `setState` on completion, you can warn or update stale UI. The usual fix is an **“is cancelled” flag** set in cleanup, checked after `await`—exactly the pattern described in **[doc/frontend.md](../doc/frontend.md)** for the briefings load.

---

## Part 6 — Derived values: `useMemo`

If something can be computed from state/props **without storing duplicate state**, compute it during render. For **expensive** derivations or **referential stability** (when passing objects/arrays as deps to memoized children or effects), wrap in **`useMemo`**:

```tsx
import { useMemo, useState } from 'react'

function Demo() {
  const [ids, setIds] = useState<string[]>(['a', 'b'])
  const [selected, setSelected] = useState('a')

  const selectedLabel = useMemo(
    () => ids.find((id) => id === selected) ?? '(none)',
    [ids, selected],
  )

  return <p>{selectedLabel}</p>
}
```

Do not `useMemo` everything by default—**measure or reason** about cost. In small apps, clarity beats micro-optimization.

---

## Part 7 — Refs: `useRef`

**`useRef`** holds a **mutable `.current` field** that persists for the full lifetime of a mounted component. Updating `.current` does **not** trigger a re-render.

Two common uses:

1. **DOM reference** — pass `ref={myRef}` to a native element; read `myRef.current` for focus, measurements, or **outside-click** detection.
2. **Stable box** for any value (timers, last rendered props) without re-render on change.

---

## Part 8 — Lists, `key`, and conditional UI

### Rendering lists

Use **`Array.prototype.map`** to arrays of data → arrays of elements.

### `key`

Each sibling in a list should have a **stable `key`** (usually a server id). Keys help React **match identity** across reorders and inserts. **Do not** use array index as key when the list can reorder or delete from the middle—identity bugs follow.

### Conditional rendering

Use **`&&`**, ternary, or early `return` for branches. Favor readable branches over clever one-liners in production code.

```tsx
{loading ? <p>Loading…</p> : error ? <p>{error}</p> : <ul>{items.map(…)}</ul>}
```

---

## Part 9 — Events and accessibility

### Synthetic events

React wraps native events for consistency. **`onClick`** receives a **mouse-like** event object; call **`event.preventDefault()`** when you need to block default browser behavior.

### Accessibility (a11y)

Prefer **semantic HTML** (`button` for actions, `nav`, `main`, headings). Use **`aria-*`** when building custom widgets (menus, tabs). The product UI documents some of these choices in **[doc/frontend.md](../doc/frontend.md)** (`aria-expanded`, `role="menu"`, etc.).

---

## Part 10 — Fetching data (patterns that scale)

### Async in `useEffect`

`useEffect` cannot be `async` directly (the effect function must return either nothing or a cleanup function). The usual pattern is an **IIFE** or inner named async function:

```tsx
useEffect(() => {
  let cancelled = false
  ;(async () => {
    try {
      const res = await fetch('/api/example')
      const data = await res.json()
      if (cancelled) return
      // setState from data
    } catch (e) {
      if (cancelled) return
      // set error state
    }
  })()
  return () => {
    cancelled = true
  }
}, [])
```

### Alternatives you will see in larger apps

- **Data routers** (React Router loaders, Remix) colocate fetch with routes.
- **Query libraries** (TanStack Query) dedupe cache, retries, and stale times.

This repository stays with **`fetch` + `useEffect`** because the surface area is small—see **[react-in-this-repo.md](react-in-this-repo.md)**.

---

## Part 11 — Composition, children, and lifting state

### Children

Components can nest; the parent receives **`props.children`**. Layout components often wrap arbitrary subtree content.

### When to split components

Split when a subtree has **clear responsibility**, **re-use**, or **readability** wins—not for tiny one-line fragments unless tests or Storybook benefit.

---

## Part 12 — Third-party React components

Libraries like **`react-markdown`** expose **React components** you embed in your tree. Props become **configuration**; children or string props become **content**. The briefing reader uses this for Markdown rendering—mapped in **[react-in-this-repo.md](react-in-this-repo.md)**.

---

## Part 13 — Build tooling: Vite (mental model)

**Vite** is a dev server and bundler. In development, it serves modules with **fast refresh** (hot updates). **`@vitejs/plugin-react`** compiles JSX/TSX and applies React refresh rules.

**`import`** paths are resolved at build time. **`import './App.css'`** injects styles (mechanism depends on bundler; in Vite, side-effect imports are common for global/component CSS).

**Environment variables** prefixed with **`VITE_`** are exposed to client code; see **`briefing-ui/vite.config.ts`** for proxy configuration that keeps browser requests on the same origin (`/api` → backend).

---

## Part 14 — Common pitfalls (short list)

| Symptom | Likely cause |
|--------|----------------|
| “Maximum update depth exceeded” | `setState` during render or effect without stable deps → infinite loop |
| Stale UI after async | Missing deps; closure captured old state—use functional updates `setX(prev => …)` or correct deps |
| Effect runs too often | Object/array literals in deps—lift them out or memoize |
| List warnings / wrong rows | Unstable or duplicate **`key`** |
| Strict Mode double fetch in dev | Expected; use cancellation or idempotent APIs |

---

## Part 15 — Learning path with this repository

1. Solidify **modern JavaScript** (modules, promises, `async`/`await`).
2. Read this document and build a toy component (`useState` + `useEffect` + `fetch`).
3. Read **[react-in-this-repo.md](react-in-this-repo.md)** alongside **`briefing-ui/src/App.tsx`**.
4. Read **[doc/frontend.md](../doc/frontend.md)** for commands, proxy, and runtime flow; **[doc/briefing-api.md](../doc/briefing-api.md)** for HTTP semantics the UI depends on.

---

## Authoritative documentation

- [React reference — Hooks](https://react.dev/reference/react) — canonical rules for `useState`, `useEffect`, etc.
- [React reference — `react-dom` client APIs](https://react.dev/reference/react-dom/client) — `createRoot`.
- [Vite](https://vite.dev/guide/) — config, env, proxy.

React’s official docs are reorganized frequently; when examples disagree with your exact minor version, **trust the official reference** for hook rules and lifecycle semantics.
