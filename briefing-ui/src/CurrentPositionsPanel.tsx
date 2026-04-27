import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  createPositions,
  deletePositionBySymbol,
  fetchPositions,
  updatePosition,
} from './api/positions'
import type { StockPosition } from './types'

type PanelMessage = { kind: 'ok' | 'err'; text: string } | null

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/** Value for {@code <input type="datetime-local" />} in local wall time. */
function instantToDatetimeLocalValue(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

function datetimeLocalToInstant(value: string): string {
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) {
    throw new Error('Invalid date')
  }
  return d.toISOString()
}

function quantityToNumber(q: StockPosition['quantity']): number {
  if (typeof q === 'number' && !Number.isNaN(q)) return q
  const n = Number(q)
  return Number.isFinite(n) ? n : 0
}

function formatQuantity(q: StockPosition['quantity']): string {
  const n = quantityToNumber(q)
  return String(n)
}

function formatInstantDisplay(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(d)
}

const defaultOpenedLocal = (): string => {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

type FormMode = 'closed' | 'add' | 'edit'

/** Rendered only when the holdings view is active; remount refetches on return. */
export function CurrentPositionsPanel() {
  const [positions, setPositions] = useState<StockPosition[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [message, setMessage] = useState<PanelMessage>(null)
  const [formMode, setFormMode] = useState<FormMode>('closed')
  const [formSymbol, setFormSymbol] = useState('')
  const [formQuantity, setFormQuantity] = useState('')
  const [formOpenedLocal, setFormOpenedLocal] = useState(defaultOpenedLocal)
  const [submitting, setSubmitting] = useState(false)

  const reload = useCallback(async () => {
    setLoadError(null)
    const data = await fetchPositions()
    setPositions(data)
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setLoadError(null)
      try {
        const data = await fetchPositions()
        if (!cancelled) setPositions(data)
      } catch (e) {
        if (!cancelled) {
          setLoadError(e instanceof Error ? e.message : 'Failed to load positions')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!message || message.kind !== 'ok') return
    const t = window.setTimeout(() => setMessage(null), 5000)
    return () => window.clearTimeout(t)
  }, [message])

  function openAdd() {
    setFormMode('add')
    setFormSymbol('')
    setFormQuantity('')
    setFormOpenedLocal(defaultOpenedLocal())
    setMessage(null)
  }

  function openEdit(p: StockPosition) {
    setFormMode('edit')
    setFormSymbol(p.symbol)
    setFormQuantity(formatQuantity(p.quantity))
    setFormOpenedLocal(
      p.openedAt ? instantToDatetimeLocalValue(p.openedAt) : defaultOpenedLocal(),
    )
    setMessage(null)
  }

  function closeForm() {
    setFormMode('closed')
  }

  async function onSubmitForm(ev: FormEvent) {
    ev.preventDefault()
    const sym = formSymbol.trim().toUpperCase()
    if (!sym) {
      setMessage({ kind: 'err', text: 'Symbol is required.' })
      return
    }
    const qty = Number(formQuantity)
    if (!Number.isFinite(qty)) {
      setMessage({ kind: 'err', text: 'Quantity must be a number.' })
      return
    }
    if (!formOpenedLocal.trim()) {
      setMessage({ kind: 'err', text: 'Opened date and time are required.' })
      return
    }
    let openedIso: string
    try {
      openedIso = datetimeLocalToInstant(formOpenedLocal)
    } catch {
      setMessage({ kind: 'err', text: 'Invalid opened date.' })
      return
    }

    setSubmitting(true)
    setMessage(null)
    try {
      if (formMode === 'add') {
        await createPositions([{ symbol: sym, quantity: qty, opened_at: openedIso }])
        setMessage({ kind: 'ok', text: `Added position ${sym}.` })
      } else {
        await updatePosition(sym, { quantity: qty, opened_at: openedIso })
        setMessage({ kind: 'ok', text: `Updated ${sym}.` })
      }
      await reload()
      closeForm()
    } catch (e) {
      setMessage({
        kind: 'err',
        text: e instanceof Error ? e.message : 'Save failed',
      })
    } finally {
      setSubmitting(false)
    }
  }

  async function onDelete(p: StockPosition) {
    if (
      !window.confirm(
        `Remove position ${p.symbol}? This cannot be undone.`,
      )
    ) {
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      await deletePositionBySymbol(p.symbol)
      await reload()
      if (formMode === 'edit' && formSymbol.toUpperCase() === p.symbol) {
        closeForm()
      }
      setMessage({ kind: 'ok', text: `Removed ${p.symbol}.` })
    } catch (e) {
      setMessage({
        kind: 'err',
        text: e instanceof Error ? e.message : 'Delete failed',
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="positions-panel">
      <div className="positions-panel-head">
        <div>
          <h2 className="positions-panel-title">Current positions</h2>
          <p className="positions-panel-lead">
            One row per symbol. Used when generating news briefings.
          </p>
        </div>
        {formMode === 'closed' ? (
          <button
            type="button"
            className="positions-btn positions-btn--primary"
            onClick={openAdd}
          >
            Add position
          </button>
        ) : (
          <button
            type="button"
            className="positions-btn"
            onClick={closeForm}
            disabled={submitting}
          >
            Cancel
          </button>
        )}
      </div>

      {message ? (
        <div
          className={`positions-banner positions-banner--${message.kind}`}
          role={message.kind === 'err' ? 'alert' : 'status'}
        >
          {message.text}
        </div>
      ) : null}

      {formMode !== 'closed' ? (
        <form className="positions-form" onSubmit={(e) => void onSubmitForm(e)}>
          <div className="positions-form-grid">
            <label className="positions-field">
              <span className="positions-field-label">Symbol</span>
              <input
                className="positions-input"
                type="text"
                name="symbol"
                autoComplete="off"
                spellCheck={false}
                value={formSymbol}
                onChange={(e) => setFormSymbol(e.target.value)}
                disabled={formMode === 'edit' || submitting}
                placeholder="e.g. AAPL"
              />
            </label>
            <label className="positions-field">
              <span className="positions-field-label">Quantity</span>
              <input
                className="positions-input"
                type="number"
                name="quantity"
                inputMode="decimal"
                step="any"
                value={formQuantity}
                onChange={(e) => setFormQuantity(e.target.value)}
                disabled={submitting}
              />
            </label>
            <label className="positions-field positions-field--wide">
              <span className="positions-field-label">Opened</span>
              <input
                className="positions-input"
                type="datetime-local"
                name="opened"
                value={formOpenedLocal}
                onChange={(e) => setFormOpenedLocal(e.target.value)}
                disabled={submitting}
              />
            </label>
          </div>
          <div className="positions-form-actions">
            <button
              type="submit"
              className="positions-btn positions-btn--primary"
              disabled={submitting}
            >
              {formMode === 'add' ? 'Save position' : 'Save changes'}
            </button>
          </div>
        </form>
      ) : null}

      {loading ? (
        <div className="positions-state">Loading positions…</div>
      ) : loadError ? (
        <div className="positions-state positions-state--error">{loadError}</div>
      ) : positions.length === 0 ? (
        <div className="positions-state">
          No positions yet. Add a symbol to include it in briefing context.
        </div>
      ) : (
        <div className="positions-table-wrap">
          <table className="positions-table">
            <thead>
              <tr>
                <th scope="col">Symbol</th>
                <th scope="col">Quantity</th>
                <th scope="col">Opened</th>
                <th scope="col">Last updated</th>
                <th scope="col">
                  <span className="positions-sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {positions.map((p) => (
                <tr key={p.id}>
                  <td className="positions-cell-mono">{p.symbol}</td>
                  <td>{formatQuantity(p.quantity)}</td>
                  <td>{formatInstantDisplay(p.openedAt)}</td>
                  <td>{formatInstantDisplay(p.lastUpdated)}</td>
                  <td className="positions-actions-cell">
                    <button
                      type="button"
                      className="positions-link-btn"
                      onClick={() => openEdit(p)}
                      disabled={submitting}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="positions-link-btn positions-link-btn--danger"
                      onClick={() => void onDelete(p)}
                      disabled={submitting}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
