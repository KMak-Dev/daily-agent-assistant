import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { createKeyword, deleteKeywordById, fetchKeywords } from './api/keywords'
import type { WorldNewsKeyword, WorldNewsKeywordOperator } from './types'

type PanelMessage = { kind: 'ok' | 'err'; text: string } | null

/** Mirrors backend {@code WorldNewsSearchTextBuilder.build} for a live preview. */
function buildSearchPreview(rows: WorldNewsKeyword[]): string {
  let sb = ''
  for (const row of rows) {
    const term = row.keyword.trim()
    if (!term) continue
    if (row.operator === 'NOT') {
      if (sb === '') sb = `-${term}`
      else sb += ` -${term}`
    } else {
      if (sb === '') sb = term
      else sb += ` OR ${term}`
    }
  }
  return sb
}

function nextSortOrder(rows: WorldNewsKeyword[]): number {
  let max = -1
  for (const r of rows) {
    if (typeof r.sort_order === 'number' && r.sort_order > max) max = r.sort_order
  }
  return max + 1
}

type FormMode = 'closed' | 'add'

export function NewsKeywordsPanel() {
  const [rows, setRows] = useState<WorldNewsKeyword[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [message, setMessage] = useState<PanelMessage>(null)
  const [formMode, setFormMode] = useState<FormMode>('closed')
  const [formKeyword, setFormKeyword] = useState('')
  const [formOperator, setFormOperator] =
    useState<WorldNewsKeywordOperator>('OR')
  const [formSortOrder, setFormSortOrder] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const preview = useMemo(() => buildSearchPreview(rows), [rows])

  const reload = useCallback(async () => {
    setLoadError(null)
    const data = await fetchKeywords()
    setRows(data)
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setLoadError(null)
      try {
        const data = await fetchKeywords()
        if (!cancelled) setRows(data)
      } catch (e) {
        if (!cancelled) {
          setLoadError(e instanceof Error ? e.message : 'Failed to load keywords')
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
    setFormKeyword('')
    setFormOperator('OR')
    setFormSortOrder('')
    setMessage(null)
  }

  function closeForm() {
    setFormMode('closed')
  }

  async function onSubmitForm(ev: FormEvent) {
    ev.preventDefault()
    const kw = formKeyword.trim()
    if (!kw) {
      setMessage({ kind: 'err', text: 'Keyword is required.' })
      return
    }
    let sort_order: number | null = null
    if (formSortOrder.trim() !== '') {
      const n = Number(formSortOrder)
      if (!Number.isFinite(n) || !Number.isInteger(n)) {
        setMessage({ kind: 'err', text: 'Sort order must be a whole number.' })
        return
      }
      sort_order = n
    }

    setSubmitting(true)
    setMessage(null)
    try {
      const body =
        sort_order === null
          ? { keyword: kw, operator: formOperator }
          : { keyword: kw, operator: formOperator, sort_order }
      await createKeyword(body)
      setMessage({ kind: 'ok', text: `Added keyword “${kw}”.` })
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

  async function onDelete(row: WorldNewsKeyword) {
    if (
      !window.confirm(
        `Remove keyword “${row.keyword}”? World News ingest will rebuild its search text.`,
      )
    ) {
      return
    }
    setSubmitting(true)
    setMessage(null)
    try {
      await deleteKeywordById(row.id)
      await reload()
      setMessage({ kind: 'ok', text: `Removed “${row.keyword}”.` })
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
          <h2 className="positions-panel-title">News keywords</h2>
          <p className="positions-panel-lead">
            Terms used for World News search ingest. OR joins positives; NOT
            excludes (same rules as the backend query builder).
          </p>
        </div>
        {formMode === 'closed' ? (
          <button
            type="button"
            className="positions-btn positions-btn--primary"
            onClick={openAdd}
          >
            Add keyword
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

      {!loading && !loadError && rows.length > 0 ? (
        <div className="keywords-preview" aria-live="polite">
          <span className="keywords-preview-label">Search text preview</span>
          <code className="keywords-preview-code">
            {preview || '(empty — ingest may use env default text)'}
          </code>
        </div>
      ) : null}

      {formMode !== 'closed' ? (
        <form className="positions-form" onSubmit={(e) => void onSubmitForm(e)}>
          <div className="positions-form-grid">
            <label className="positions-field positions-field--wide">
              <span className="positions-field-label">Keyword</span>
              <input
                className="positions-input"
                type="text"
                name="keyword"
                autoComplete="off"
                spellCheck={false}
                value={formKeyword}
                onChange={(e) => setFormKeyword(e.target.value)}
                disabled={submitting}
                placeholder="e.g. silver"
              />
            </label>
            <label className="positions-field">
              <span className="positions-field-label">Operator</span>
              <select
                className="positions-input"
                name="operator"
                value={formOperator}
                onChange={(e) =>
                  setFormOperator(e.target.value as WorldNewsKeywordOperator)
                }
                disabled={submitting}
              >
                <option value="OR">OR (include)</option>
                <option value="NOT">NOT (exclude)</option>
              </select>
            </label>
            <label className="positions-field">
              <span className="positions-field-label">Sort order</span>
              <input
                className="positions-input"
                type="number"
                name="sort_order"
                inputMode="numeric"
                step={1}
                value={formSortOrder}
                onChange={(e) => setFormSortOrder(e.target.value)}
                disabled={submitting}
                placeholder={String(nextSortOrder(rows))}
                title="Leave blank to use server default (0), or set for ordering"
              />
            </label>
          </div>
          <div className="positions-form-actions">
            <button
              type="submit"
              className="positions-btn positions-btn--primary"
              disabled={submitting}
            >
              Save keyword
            </button>
          </div>
        </form>
      ) : null}

      {loading ? (
        <div className="positions-state">Loading keywords…</div>
      ) : loadError ? (
        <div className="positions-state positions-state--error">{loadError}</div>
      ) : rows.length === 0 ? (
        <div className="positions-state">
          No keywords yet. Add terms to drive World News search, or rely on
          configured default text when the collection is empty.
        </div>
      ) : (
        <div className="positions-table-wrap">
          <table className="positions-table">
            <thead>
              <tr>
                <th scope="col">Order</th>
                <th scope="col">Keyword</th>
                <th scope="col">Operator</th>
                <th scope="col">
                  <span className="positions-sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.sort_order}</td>
                  <td className="positions-cell-mono">{r.keyword}</td>
                  <td>{r.operator}</td>
                  <td className="positions-actions-cell">
                    <button
                      type="button"
                      className="positions-link-btn positions-link-btn--danger"
                      onClick={() => void onDelete(r)}
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
