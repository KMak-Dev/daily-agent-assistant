import type {
  NewsAnalyzeResponse,
  NewsBriefingPageResponse,
  NewsDailyBriefing,
} from '../types'

/** Inclusive calendar-day window for the sidebar (local device dates). */
export const BRIEFING_SIDEBAR_RECENT_DAYS = 90

/** Matches Spring `PaginationConfig` max page size (100). */
const BRIEFINGS_PAGE_SIZE = 100

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

function localCalendarTodayIso(): string {
  const n = new Date()
  return `${n.getFullYear()}-${pad2(n.getMonth() + 1)}-${pad2(n.getDate())}`
}

function addLocalCalendarDays(isoDate: string, deltaDays: number): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate.trim())
  if (!m) throw new Error(`Invalid ISO date: ${isoDate}`)
  const dt = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  dt.setDate(dt.getDate() + deltaDays)
  return `${dt.getFullYear()}-${pad2(dt.getMonth() + 1)}-${pad2(dt.getDate())}`
}

/**
 * Inclusive `from` / `to` on stored briefing `startDate` (GET `/api/news/briefings`).
 * Uses the browser's local calendar for "today" and the preceding
 * `BRIEFING_SIDEBAR_RECENT_DAYS - 1` days for `from`.
 */
export function briefingSidebarDateRange(): { from: string; to: string } {
  const to = localCalendarTodayIso()
  const from = addLocalCalendarDays(to, -(BRIEFING_SIDEBAR_RECENT_DAYS - 1))
  return { from, to }
}

/** Newest `startDate` first; ties broken by `endDate`, then `timeZone`. */
function compareBriefingsByWindowDesc(
  a: NewsDailyBriefing,
  b: NewsDailyBriefing,
): number {
  const byStart = b.startDate.localeCompare(a.startDate)
  if (byStart !== 0) return byStart
  const byEnd = b.endDate.localeCompare(a.endDate)
  if (byEnd !== 0) return byEnd
  return a.timeZone.localeCompare(b.timeZone)
}

async function fetchBriefingsPage(
  from: string,
  to: string,
  page: number,
): Promise<NewsBriefingPageResponse> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(BRIEFINGS_PAGE_SIZE),
    sort: 'startDate,desc',
    from,
    to,
  })
  const url = `/api/news/briefings?${params.toString()}`
  const res = await fetch(url, { headers: { Accept: 'application/json' } })
  const body = await res.text()

  if (!res.ok) {
    throw new Error(body || `Request failed (${res.status})`)
  }

  return JSON.parse(body) as NewsBriefingPageResponse
}

/**
 * Archived briefings whose window `startDate` falls in the last `BRIEFING_SIDEBAR_RECENT_DAYS`
 * local calendar days (inclusive), newest `startDate` first (`sort=startDate,desc` plus stable
 * client reorder). Fetches every page until the server reports `last`.
 */
export async function fetchRecentBriefings(): Promise<NewsDailyBriefing[]> {
  const { from, to } = briefingSidebarDateRange()
  const merged: NewsDailyBriefing[] = []
  let page = 0
  const maxPages = 50

  while (page < maxPages) {
    const data = await fetchBriefingsPage(from, to, page)
    if (!Array.isArray(data.items)) {
      throw new Error('Invalid response: missing items array')
    }
    merged.push(...data.items)
    if (data.last || data.items.length === 0) break
    page += 1
  }

  return [...merged].sort(compareBriefingsByWindowDesc)
}

/** Re-runs analyze for the same window and upserts the archive row (`POST /api/news/analyze`). */
export async function rerunBriefingAnalysis(
  b: Pick<NewsDailyBriefing, 'startDate' | 'endDate' | 'timeZone'>,
): Promise<NewsAnalyzeResponse> {
  const res = await fetch('/api/news/analyze', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      startDate: b.startDate,
      endDate: b.endDate,
      timeZone: b.timeZone,
    }),
  })
  const body = await res.text()
  if (!res.ok) {
    throw new Error(body.trim() || `Request failed (${res.status})`)
  }
  return JSON.parse(body) as NewsAnalyzeResponse
}

/** Deletes one archived briefing by Mongo id (`DELETE /api/news/briefings/{id}`). */
export async function deleteArchivedBriefing(id: string): Promise<void> {
  const res = await fetch(`/api/news/briefings/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
  const text = await res.text()
  if (!res.ok) {
    throw new Error(text.trim() || `Request failed (${res.status})`)
  }
}
