import type {
  NewsAnalyzeResponse,
  NewsBriefingPageResponse,
  NewsDailyBriefing,
} from '../types'

const SIDEBAR_LIMIT = 7

/** Newest {@code startDate} first; ties broken by {@code endDate}, then {@code timeZone}. */
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

/**
 * Seven archived briefings, **newest {@code startDate} first** (Spring {@code sort=startDate,desc}
 * plus a stable client reorder).
 */
export async function fetchRecentBriefings(): Promise<NewsDailyBriefing[]> {
  const params = new URLSearchParams({
    page: '0',
    size: String(SIDEBAR_LIMIT),
    sort: 'startDate,desc',
  })
  const url = `/api/news/briefings?${params.toString()}`
  const res = await fetch(url, { headers: { Accept: 'application/json' } })
  const body = await res.text()

  if (!res.ok) {
    throw new Error(body || `Request failed (${res.status})`)
  }

  const data = JSON.parse(body) as NewsBriefingPageResponse
  if (!Array.isArray(data.items)) {
    throw new Error('Invalid response: missing items array')
  }

  return [...data.items].sort(compareBriefingsByWindowDesc)
}

/**
 * Re-runs the news analyze pipeline for the same window as an archived briefing and upserts the
 * archive row (see {@code POST /api/news/analyze}).
 */
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

/** Deletes one archived briefing by Mongo id ({@code DELETE /api/news/briefings/{id}}). */
export async function deleteArchivedBriefing(id: string): Promise<void> {
  const res = await fetch(`/api/news/briefings/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
  const text = await res.text()
  if (!res.ok) {
    throw new Error(text.trim() || `Request failed (${res.status})`)
  }
}
