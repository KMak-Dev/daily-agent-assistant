import type { WorldNewsKeyword, WorldNewsKeywordOperator } from '../types'

export async function fetchKeywords(): Promise<WorldNewsKeyword[]> {
  const res = await fetch('/api/world-news/keywords', {
    headers: { Accept: 'application/json' },
  })
  const body = await res.text()
  if (!res.ok) {
    throw new Error(body.trim() || `Request failed (${res.status})`)
  }
  const data = JSON.parse(body) as unknown
  if (!Array.isArray(data)) {
    throw new Error('Invalid response: expected a JSON array')
  }
  return data as WorldNewsKeyword[]
}

export type CreateKeywordBody = {
  keyword: string
  operator: WorldNewsKeywordOperator
  sort_order?: number | null
}

export async function createKeyword(
  body: CreateKeywordBody,
): Promise<WorldNewsKeyword> {
  const res = await fetch('/api/world-news/keywords', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) {
    throw new Error(text.trim() || `Request failed (${res.status})`)
  }
  return JSON.parse(text) as WorldNewsKeyword
}

export async function deleteKeywordById(id: string): Promise<void> {
  const enc = encodeURIComponent(id)
  const res = await fetch(`/api/world-news/keywords/${enc}`, {
    method: 'DELETE',
  })
  if (res.status === 204) return
  const t = (await res.text()).trim()
  throw new Error(t || `Request failed (${res.status})`)
}
