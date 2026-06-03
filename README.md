# daily-agent-assistant

News ingest, analysis, and briefing UI backed by Spring Boot and MongoDB.

## Quick start (Docker)

From this directory:

```bash
cp .env.example .env
# Edit .env — at minimum WORLD_NEWS_API_KEY and XAI_API_KEY for ingest/analyze
docker compose up --build
```

Open **http://localhost** (set `WEB_PORT` in `.env` if port 80 is taken). The API is also on **http://localhost:8080** for curl and `scripts/run-daily-news-briefing.sh`.

See **doc/backend.md**, **doc/frontend.md**, and **doc/briefing-api.md**.
