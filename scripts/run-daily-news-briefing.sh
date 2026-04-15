#!/usr/bin/env bash
# Calls POST /api/news/analyze for a half-open window [startDate, endDate) in TIME_ZONE.
# Default: anchor = previous calendar day (DAY_OFFSET=1), span WINDOW_DAYS calendar days (matches NEWS_DAILY_BRIEFING_*).
# Override anchor with DAY=2026-04-10. WINDOW_DAYS is clamped to 1..7 (same as the API).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TIME_ZONE="${TIME_ZONE:-Asia/Hong_Kong}"
DAY_OFFSET="${DAY_OFFSET:-1}"
WINDOW_DAYS="${WINDOW_DAYS:-1}"
if [[ "${WINDOW_DAYS}" -lt 1 ]]; then WINDOW_DAYS=1; fi
if [[ "${WINDOW_DAYS}" -gt 7 ]]; then WINDOW_DAYS=7; fi
BRIEFING_PROMPT="${BRIEFING_PROMPT:-Give a briefing on the summarized news relevant to our stock positions ONLY below. Analyze how the themes and events may affect our current stock positions (symbols and quantities are listed under \"Our current stock positions\"). Tie commentary to our holdings only when the news summaries reasonably support it. Do not invent facts, stories, or positions; if you want to be speculative, you are allowed to but say so clearly.}"

if [[ -n "${DAY:-}" ]]; then
  target="$DAY"
else
  if TZ="$TIME_ZONE" date -v-"${DAY_OFFSET}"d +%Y-%m-%d >/dev/null 2>&1; then
    target="$(TZ="$TIME_ZONE" date -v-"${DAY_OFFSET}"d +%Y-%m-%d)"
  else
    target="$(TZ="$TIME_ZONE" date -d "now -${DAY_OFFSET} days" +%Y-%m-%d)"
  fi
fi

if target_end="$(date -j -v+"${WINDOW_DAYS}"d -f "%Y-%m-%d" "$target" +%Y-%m-%d 2>/dev/null)"; then
  :
elif target_end="$(date -d "${target} +${WINDOW_DAYS} days" +%Y-%m-%d 2>/dev/null)"; then
  :
else
  echo "need BSD or GNU date to compute endDate (${target} + ${WINDOW_DAYS} days)" >&2
  exit 1
fi

if command -v jq >/dev/null 2>&1; then
  payload="$(
    jq -n \
      --arg d "$target" \
      --arg e "$target_end" \
      --arg tz "$TIME_ZONE" \
      --arg p "$BRIEFING_PROMPT" \
      '{startDate:$d,endDate:$e,timeZone:$tz,batchSize:10,refreshSummaries:false,briefingPrompt:$p}'
  )"
elif command -v python3 >/dev/null 2>&1; then
  payload="$(
    BRIEFING_PROMPT="$BRIEFING_PROMPT" TARGET="$target" TARGET_END="$target_end" TIME_ZONE="$TIME_ZONE" python3 - <<'PY'
import json, os
print(json.dumps({
  "startDate": os.environ["TARGET"],
  "endDate": os.environ["TARGET_END"],
  "timeZone": os.environ["TIME_ZONE"],
  "batchSize": 10,
  "refreshSummaries": False,
  "briefingPrompt": os.environ["BRIEFING_PROMPT"],
}))
PY
  )"
else
  echo "install jq or python3 to build the JSON body" >&2
  exit 1
fi

curl -sS -X POST "${BASE_URL%/}/api/news/analyze" \
  -H 'Content-Type: application/json' \
  -d "$payload"
echo
