export interface NewsDailyBriefing {
  id: string
  startDate: string
  endDate: string
  timeZone: string
  articleCount: number
  briefing: string
  lastSource?: string
  createdAt: string
  updatedAt: string
}

export interface NewsBriefingPageResponse {
  items: NewsDailyBriefing[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/** Body of {@code POST /api/news/analyze} (200). */
export interface NewsAnalyzeResponse {
  startDate: string
  endDate: string
  timeZone: string
  articleCount: number
  summariesFilledThisRun: number
  briefing: string
}

/** {@code GET /api/positions} row — {@code quantity} may be number or string from JSON. */
export interface StockPosition {
  id: string
  symbol: string
  quantity: number | string
  openedAt: string
  lastUpdated: string
}

export type WorldNewsKeywordOperator = 'OR' | 'NOT'

/** {@code GET /api/world-news/keywords} row — matches {@link WorldNewsKeywordResponse}. */
export interface WorldNewsKeyword {
  id: string
  keyword: string
  operator: WorldNewsKeywordOperator
  sort_order: number
}
