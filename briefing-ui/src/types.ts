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
