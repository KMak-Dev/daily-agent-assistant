import type { StockPosition } from '../types'

export async function fetchPositions(): Promise<StockPosition[]> {
  const res = await fetch('/api/positions', {
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
  return data as StockPosition[]
}

export type CreatePositionItem = {
  symbol: string
  quantity: number
  opened_at: string
}

/** Bulk create; pass a one-element array for a single add. */
export async function createPositions(
  items: CreatePositionItem[],
): Promise<StockPosition[]> {
  const res = await fetch('/api/positions', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(items),
  })
  const body = await res.text()
  if (!res.ok) {
    throw new Error(body.trim() || `Request failed (${res.status})`)
  }
  return JSON.parse(body) as StockPosition[]
}

export async function updatePosition(
  symbol: string,
  patch: { quantity?: number; opened_at?: string },
): Promise<StockPosition> {
  const sym = encodeURIComponent(symbol)
  const res = await fetch(`/api/positions/${sym}`, {
    method: 'PUT',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(patch),
  })
  const body = await res.text()
  if (!res.ok) {
    throw new Error(body.trim() || `Request failed (${res.status})`)
  }
  return JSON.parse(body) as StockPosition
}

export async function deletePositionBySymbol(symbol: string): Promise<void> {
  const q = new URLSearchParams({ symbol })
  const res = await fetch(`/api/positions?${q.toString()}`, {
    method: 'DELETE',
  })
  if (res.status === 204) return
  const text = (await res.text()).trim()
  throw new Error(text || `Request failed (${res.status})`)
}
