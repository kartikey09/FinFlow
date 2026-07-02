# FinFlow Dashboard

Minimal Vite + React 18 + TypeScript UI for the FinFlow project. One page, three
regions — spend chart, commitment table, ingest trigger.

Runs on `localhost:3000` and proxies `/api/*` to the query-api on `localhost:8087`.

## Prereq

- Node 20 or later
- The query-api running (`./gradlew :services:query-api:bootRun`)

## Run

```bash
cd web/dashboard
npm install
npm run dev
```

Open http://localhost:3000.

## What you should see

- **Spend by team · last 30 days** — bar chart of accumulated cost per team,
  refreshed every 5 s. Empty until the ingestors have polled and the normalizer
  has projected.
- **Commitment utilization** — one row per commitment with the latest
  utilization fraction and any triggered alert (`UNDERUTILIZED`, `SATURATED`,
  `EXPIRING_SOON`).
- **Trigger pull** — pings the ingestor's `/actuator/health`. The actual poll
  runs on its schedule (~30 s cadence). Wire a `POST /trigger` on the ingestor
  side if you want the button to force an immediate cycle.
