import { useEffect, useMemo, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import {
  BRIEFING_SIDEBAR_RECENT_DAYS,
  deleteArchivedBriefing,
  fetchRecentBriefings,
  rerunBriefingAnalysis,
} from './api/briefings'
import { CurrentPositionsPanel } from './CurrentPositionsPanel'
import { NewsKeywordsPanel } from './NewsKeywordsPanel'
import type { NewsDailyBriefing } from './types'
import './App.css'

/** Backend {@code endDate} is exclusive; this is the last calendar day covered (ISO date). */
function exclusiveEndToInclusiveLastDay(isoEnd: string): string | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoEnd.trim())
  if (!m) return null
  const t = Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  const prev = new Date(t - 86400000)
  const y = prev.getUTCFullYear()
  const mo = String(prev.getUTCMonth() + 1).padStart(2, '0')
  const day = String(prev.getUTCDate()).padStart(2, '0')
  return `${y}-${mo}-${day}`
}

/** Human-readable article window: inclusive range; API still uses exclusive {@code endDate}. */
function formatBriefingWindowDisplay(b: NewsDailyBriefing): string {
  const displayEnd = exclusiveEndToInclusiveLastDay(b.endDate)
  if (displayEnd === null || displayEnd < b.startDate) {
    if (b.startDate === b.endDate) return b.startDate
    return `${b.startDate} \u2013 ${b.endDate}`
  }
  if (b.startDate === displayEnd) return b.startDate
  return `${b.startDate} \u2013 ${displayEnd}`
}

/** Last calendar day covered (inclusive); falls back to raw {@code endDate} if parsing fails. */
function inclusiveCoverageEndDate(b: NewsDailyBriefing): string {
  const displayEnd = exclusiveEndToInclusiveLastDay(b.endDate)
  if (displayEnd === null || displayEnd < b.startDate) return b.endDate
  return displayEnd
}

/**
 * e.g. {@code Thu, April 23, 2026 at 3:26 PM GMT+8} — wall time in {@code timeZone} (IANA).
 */
function formatBriefingUpdatedAtInZone(isoInstant: string, timeZone: string): string {
  const d = new Date(isoInstant)
  if (Number.isNaN(d.getTime())) return isoInstant
  try {
    const datePart = new Intl.DateTimeFormat('en-US', {
      timeZone,
      weekday: 'short',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    }).format(d)

    const timePart = new Intl.DateTimeFormat('en-US', {
      timeZone,
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
      timeZoneName: 'shortOffset',
    }).format(d)

    return `${datePart} at ${timePart}`
  } catch {
    return `${isoInstant} (${timeZone})`
  }
}

/** First markdown block (through the first blank line); rest is the remainder of the doc. */
function splitBriefingOpening(markdown: string): { opening: string; rest: string } {
  const normalized = markdown.trim().replace(/\r\n/g, '\n')
  if (!normalized) return { opening: '', rest: '' }
  const parts = normalized.split(/\n\s*\n/)
  const opening = (parts[0] ?? '').trim()
  const rest = parts.slice(1).join('\n\n').trim()
  return { opening, rest }
}

type AppView = 'briefings' | 'holdings' | 'keywords'

const TOOLBAR_NAV_ITEMS: { view: AppView; label: string }[] = [
  { view: 'briefings', label: 'Briefing on assets' },
  { view: 'holdings', label: 'Current positions' },
  { view: 'keywords', label: 'News keywords' },
]

function App() {
  const [rows, setRows] = useState<NewsDailyBriefing[]>([])
  const [appView, setAppView] = useState<AppView>('briefings')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionsMenuOpen, setActionsMenuOpen] = useState(false)
  const [overflowBusy, setOverflowBusy] = useState(false)
  const [actionBanner, setActionBanner] = useState<{
    kind: 'ok' | 'err'
    message: string
  } | null>(null)
  const [readerScrolled, setReaderScrolled] = useState(false)
  const actionsWrapRef = useRef<HTMLDivElement>(null)
  const mainReaderRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError(null)
      try {
        const data = await fetchRecentBriefings()
        if (cancelled) return
        setRows(data)
        setSelectedId((prev) => {
          if (prev && data.some((r) => r.id === prev)) return prev
          return data[0]?.id ?? null
        })
      } catch (e) {
        if (cancelled) return
        setError(e instanceof Error ? e.message : 'Failed to load briefings')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!actionBanner || actionBanner.kind !== 'ok') return
    const t = window.setTimeout(() => setActionBanner(null), 5000)
    return () => window.clearTimeout(t)
  }, [actionBanner])

  useEffect(() => {
    const el = mainReaderRef.current
    if (el) {
      el.scrollTop = 0
    }
    setReaderScrolled(false)
  }, [appView, selectedId])

  useEffect(() => {
    if (appView !== 'briefings') {
      setActionsMenuOpen(false)
      setActionBanner(null)
    }
  }, [appView])

  function syncReaderScrollShadow() {
    const el = mainReaderRef.current
    if (!el) return
    setReaderScrolled(el.scrollTop > 2)
  }

  const selected = useMemo(
    () => rows.find((r) => r.id === selectedId) ?? null,
    [rows, selectedId],
  )

  const briefingParts = useMemo(() => {
    if (!selected) return { opening: '', rest: '' }
    const raw = selected.briefing?.trim()
      ? selected.briefing.trim()
      : '_Empty briefing._'
    return splitBriefingOpening(raw)
  }, [selected])

  useEffect(() => {
    if (!actionsMenuOpen) return

    const onPointerDown = (ev: PointerEvent) => {
      const el = actionsWrapRef.current
      if (el && !el.contains(ev.target as Node)) {
        setActionsMenuOpen(false)
      }
    }

    const onKeyDown = (ev: KeyboardEvent) => {
      if (ev.key === 'Escape') {
        setActionsMenuOpen(false)
      }
    }

    document.addEventListener('pointerdown', onPointerDown, true)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown, true)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [actionsMenuOpen])

  async function onRerunAnalysis() {
    if (!selected || overflowBusy) return
    setOverflowBusy(true)
    setActionBanner(null)
    try {
      await rerunBriefingAnalysis(selected)
      const data = await fetchRecentBriefings()
      setRows(data)
      setSelectedId((prev) => {
        if (prev && data.some((r) => r.id === prev)) return prev
        return data[0]?.id ?? null
      })
      setActionBanner({
        kind: 'ok',
        message: 'Briefing refreshed from a new analysis run.',
      })
    } catch (e) {
      setActionBanner({
        kind: 'err',
        message: e instanceof Error ? e.message : 'Rerun failed',
      })
    } finally {
      setOverflowBusy(false)
      setActionsMenuOpen(false)
    }
  }

  async function onDeleteFromArchive() {
    if (!selected || overflowBusy) return
    if (
      !window.confirm(
        'Remove this briefing from the archive? This cannot be undone.',
      )
    ) {
      return
    }
    setOverflowBusy(true)
    setActionBanner(null)
    const removedId = selected.id
    try {
      await deleteArchivedBriefing(removedId)
      const data = await fetchRecentBriefings()
      setRows(data)
      setSelectedId((prev) => {
        if (prev !== removedId) return prev
        return data[0]?.id ?? null
      })
      setActionBanner({
        kind: 'ok',
        message: 'Briefing removed from the archive.',
      })
    } catch (e) {
      setActionBanner({
        kind: 'err',
        message: e instanceof Error ? e.message : 'Delete failed',
      })
    } finally {
      setOverflowBusy(false)
      setActionsMenuOpen(false)
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-labelledby="sidebar-heading">
        <div className="sidebar-header">
          <h1 id="sidebar-heading">Recent briefings</h1>
          <p>
            Start dates in the last {BRIEFING_SIDEBAR_RECENT_DAYS} days — scroll the list
          </p>
        </div>
        <div className="list-scroll" role="list">
          {loading && <div className="state-block">Loading…</div>}
          {!loading && error && (
            <div className="state-block error">{error}</div>
          )}
          {!loading && !error && rows.length === 0 && (
            <div className="state-block">No briefings yet.</div>
          )}
          {!loading &&
            !error &&
            rows.map((b) => (
              <button
                key={b.id}
                type="button"
                role="listitem"
                className={`date-row${appView === 'briefings' && b.id === selectedId ? ' active' : ''}`}
                onClick={() => {
                  setSelectedId(b.id)
                  setAppView('briefings')
                  setActionsMenuOpen(false)
                }}
              >
                <span className="date-row-title">
                  Briefing on {inclusiveCoverageEndDate(b)}
                </span>
                <span className="date-row-range">
                  {formatBriefingWindowDisplay(b)}
                </span>
                <span className="date-row-meta">
                  {b.timeZone} · {b.articleCount} articles
                </span>
              </button>
            ))}
        </div>
      </aside>

      <main
        className="main"
        aria-label={
          appView === 'briefings' && selected
            ? `Briefing ${formatBriefingWindowDisplay(selected)}, ${selected.timeZone}${selected.lastSource ? `, source ${selected.lastSource}` : ''}`
            : appView === 'holdings'
              ? 'Current positions'
              : appView === 'keywords'
                ? 'News keywords'
                : undefined
        }
      >
        <div
          className={`main-toolbar-shelf${readerScrolled ? ' main-toolbar-shelf--scrolled' : ''}`}
        >
          <div className="main-measure">
            <div className="main-toolbar">
              <div className="main-toolbar-inner">
                <nav
                  className="main-toolbar-nav"
                  aria-label="Sections"
                >
                  {TOOLBAR_NAV_ITEMS.map(({ view, label }) => {
                    const isCurrent = appView === view
                    return (
                      <button
                        key={view}
                        type="button"
                        className={`main-toolbar-nav-link${isCurrent ? ' main-toolbar-nav-link--active' : ''}`}
                        aria-current={isCurrent ? 'page' : undefined}
                        onClick={() => {
                          setAppView(view)
                          setActionsMenuOpen(false)
                        }}
                      >
                        {label}
                      </button>
                    )
                  })}
                </nav>
                <div
                  className="main-toolbar-trailing"
                  aria-hidden={!(appView === 'briefings' && selected)}
                >
                  {appView === 'briefings' && selected ? (
                    <div
                      className="main-actions-wrap"
                      ref={actionsWrapRef}
                    >
                      <button
                        type="button"
                        className="main-actions-trigger"
                        id="briefing-actions-trigger"
                        aria-label="Briefing actions"
                        aria-haspopup="menu"
                        aria-expanded={actionsMenuOpen}
                        aria-controls="briefing-actions-menu"
                        aria-busy={overflowBusy}
                        disabled={overflowBusy}
                        onClick={() => setActionsMenuOpen((o) => !o)}
                      >
                        <span aria-hidden className="main-actions-trigger-dots">
                          ⋯
                        </span>
                      </button>
                      {actionsMenuOpen ? (
                        <div
                          id="briefing-actions-menu"
                          className="main-action-menu"
                          role="menu"
                          aria-labelledby="briefing-actions-trigger"
                        >
                          <button
                            type="button"
                            className="main-action-menu-item"
                            role="menuitem"
                            disabled={overflowBusy}
                            onClick={() => void onRerunAnalysis()}
                          >
                            Rerun analysis
                          </button>
                          <div
                            className="main-action-menu-sep"
                            role="separator"
                            aria-hidden
                          />
                          <button
                            type="button"
                            className="main-action-menu-item main-action-menu-item--danger"
                            role="menuitem"
                            disabled={overflowBusy}
                            onClick={() => void onDeleteFromArchive()}
                          >
                            Delete from archive
                          </button>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </div>
            </div>
            {actionBanner ? (
              <div
                className={`main-action-banner main-action-banner--${actionBanner.kind}`}
                role={actionBanner.kind === 'err' ? 'alert' : 'status'}
              >
                {actionBanner.message}
              </div>
            ) : null}
          </div>
        </div>
        <div
          ref={mainReaderRef}
          className="main-reader"
          onScroll={syncReaderScrollShadow}
        >
          <div className="main-measure">
            {appView === 'holdings' ? (
              <CurrentPositionsPanel />
            ) : appView === 'keywords' ? (
              <NewsKeywordsPanel />
            ) : !selected ? (
              <div className="state-block">Select a briefing.</div>
            ) : (
              <div className="briefing-body">
                <article>
                  <div className="briefing-headline">
                    <ReactMarkdown>{briefingParts.opening}</ReactMarkdown>
                  </div>
                  <div className="briefing-article-meta">
                    <p className="briefing-article-meta-when">
                      {formatBriefingUpdatedAtInZone(
                        selected.updatedAt,
                        selected.timeZone,
                      )}
                    </p>
                    <p className="briefing-article-meta-count">
                      {selected.articleCount === 1
                        ? '1 article read'
                        : `${selected.articleCount} articles read`}
                    </p>
                  </div>
                  {briefingParts.rest ? (
                    <div className="briefing-article-body">
                      <ReactMarkdown>{briefingParts.rest}</ReactMarkdown>
                    </div>
                  ) : null}
                </article>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}

export default App
