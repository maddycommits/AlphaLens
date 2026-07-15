# AlphaLens — Architecture Design Document

**Version:** 0.1 (draft) · **Date:** 2026-07-15 · **Author:** Maddy
**Stack:** Spring Boot 3.5 / Java 21 · Angular · Apache Kafka · PostgreSQL · Spring AI (Anthropic Claude) · Docker Compose

---

## 1. Overview

AlphaLens is an LLM-powered stock analysis platform for Indian markets (NSE/BSE).

**V1 goal:** A user maintains a watchlist (~10 stocks). Every trading day, the system detects price movements above a market-cap-relative threshold (large cap > 2%, mid cap > 3%, small cap > 4–5%), fetches related news, and uses Claude to explain *why* the stock moved (earnings, order wins, regulatory action, block deals, sector moves, etc.).

**Future goals (must not require re-architecture):** order placement via broker APIs and automated momentum trading. This is why the system is event-driven from day one — new capabilities are added by attaching new consumers to existing Kafka topics, not by modifying existing services.

---

## 2. Service Decomposition

Seven services, each independently deployable with its own database (database-per-service). The existing `alphalens` backend becomes the **reference-data-service**.

| # | Service | Responsibility | Store |
|---|---------|----------------|-------|
| 1 | **api-gateway** | Single entry point for Angular app. Routing, JWT auth, rate limiting. Spring Cloud Gateway. | — |
| 2 | **reference-data-service** (existing alphalens code) | Company master data: ticker, name, sector, exchange, market-cap classification (large/mid/small per SEBI/AMFI definitions). | PostgreSQL |
| 3 | **watchlist-service** | User accounts' watchlists (CRUD). Publishes watchlist change events so the ingestor knows what to track. | PostgreSQL |
| 4 | **market-data-service** | Polls NSE/BSE price data for all watched tickers on a schedule; computes % change vs previous close; publishes ticks. Pluggable provider interface. | PostgreSQL (EOD history) |
| 5 | **movement-detector-service** | Stateless-ish consumer of price ticks. Applies threshold rules per market-cap band (config-driven). Emits `movement.detected` events. Dedupes so one stock triggers at most once per day per direction. | Redis (dedupe state) |
| 6 | **insight-service** | On `movement.detected`: fetches news (RSS + news APIs), filters by relevance, calls Claude via Spring AI to generate a structured explanation, publishes `insight.generated`. | PostgreSQL (insights, prompt/response audit log) |
| 7 | **notification-service** | Consumes insights; delivers to user (in-app feed via WebSocket/SSE, optional email/Telegram). | PostgreSQL |

**Future services (Phase 3+, no changes to the above):**

| Service | Trigger |
|---------|---------|
| **order-service** | Consumes `order.requested` commands; integrates with broker API (Zerodha Kite Connect / Upstox / ICICI Breeze); publishes `order.placed` / `order.filled` / `order.rejected`. |
| **momentum-strategy-service** | Consumes the same `price.tick` and `insight.generated` topics; when a signal fires, publishes `order.requested`. Humans-in-the-loop flag configurable. |

### Why this shape

- **movement-detector is separate from market-data**: detection rules will grow (volume spikes, gap-ups, 52-week breakouts for momentum trading). The ingestor stays dumb and stable.
- **insight-service owns both news fetching and LLM calls**: they always happen together and share the relevance-filtering logic. Splitting them adds a network hop and a topic with no consumer diversity. Split later only if news data gets other consumers.
- **Commands vs events for trading**: `order.requested` is a *command* topic (one owner: order-service). Everything else is *events* (facts, many consumers). This keeps the future trading path auditable and safe.

---

## 3. Event-Driven Design: Kafka Topics

All events: JSON, `eventId` (UUID), `eventType`, `version`, `occurredAt` (ISO-8601 UTC), keyed by `ticker` (or `userId` where noted) for ordering. Schema evolution: additive-only within a major version; new major version = new topic suffix (`.v2`).

| Topic | Key | Producer | Consumers | Retention |
|-------|-----|----------|-----------|-----------|
| `watchlist.events.v1` | userId | watchlist-service | market-data-service | compacted |
| `price.ticks.v1` | ticker | market-data-service | movement-detector, (future: momentum-strategy) | 7 days |
| `movement.detected.v1` | ticker | movement-detector | insight-service, notification-service, (future: momentum-strategy) | 30 days |
| `insight.generated.v1` | ticker | insight-service | notification-service, (future: momentum-strategy) | 90 days |
| `insight.failed.v1` | ticker | insight-service | notification-service (fallback alert without insight) | 30 days |
| `order.requested.v1` (future) | userId | momentum-strategy / api-gateway | order-service | 7 years (audit) |
| `order.lifecycle.v1` (future) | orderId | order-service | notification-service, momentum-strategy | 7 years (audit) |

### Key event schemas

```json
// price.ticks.v1
{
  "eventId": "uuid", "eventType": "PRICE_TICK", "version": 1,
  "occurredAt": "2026-07-15T10:15:00Z",
  "ticker": "RELIANCE", "exchange": "NSE",
  "lastPrice": 2843.50, "previousClose": 2790.00,
  "changePercent": 1.92, "dayVolume": 4521000,
  "source": "yfinance"
}
```

```json
// movement.detected.v1
{
  "eventId": "uuid", "eventType": "MOVEMENT_DETECTED", "version": 1,
  "occurredAt": "2026-07-15T10:15:05Z",
  "ticker": "TATAMOTORS", "exchange": "NSE",
  "direction": "DOWN", "changePercent": -4.6,
  "thresholdApplied": 4.0, "capCategory": "MID",
  "lastPrice": 812.30, "previousClose": 851.50,
  "volumeRatio": 2.8,
  "triggerTickEventId": "uuid-of-price-tick"
}
```

```json
// insight.generated.v1
{
  "eventId": "uuid", "eventType": "INSIGHT_GENERATED", "version": 1,
  "occurredAt": "2026-07-15T10:16:40Z",
  "ticker": "TATAMOTORS",
  "movementEventId": "uuid-of-movement-event",
  "insight": {
    "headline": "JLR margin guidance cut drives 4.6% fall",
    "summary": "2-3 sentence explanation...",
    "category": "EARNINGS | GUIDANCE | ORDER_WIN | REGULATORY | MACRO | SECTOR | CORPORATE_ACTION | BLOCK_DEAL | UNKNOWN",
    "confidence": "HIGH | MEDIUM | LOW",
    "sentiment": "NEGATIVE",
    "sources": [{ "title": "...", "url": "...", "publishedAt": "..." }]
  },
  "model": "claude-sonnet-5", "promptTokens": 3200, "completionTokens": 410
}
```

`confidence: LOW` + `category: UNKNOWN` is a first-class outcome — the LLM must be prompted to say "no clear news-based cause found" rather than invent one. Notification-service renders this honestly ("moved -4.6%, no clear catalyst identified").

---

## 4. Data Flow (happy path)

```
[Scheduler in market-data-service, every 5 min during market hours 09:15–15:30 IST]
        │  poll quotes for union of all watchlisted tickers
        ▼
  price.ticks.v1 ──────────────► movement-detector
                                     │  changePercent ≥ threshold(capCategory)?
                                     │  not already triggered today (Redis)?
                                     ▼
                              movement.detected.v1 ──► notification-service (instant "RELIANCE +2.3%" alert)
                                     │
                                     ▼
                               insight-service
                                     │ 1. fetch news: Moneycontrol/ET RSS, NSE announcements RSS, NewsAPI
                                     │ 2. filter to ticker/company-name matches, last 48h
                                     │ 3. Claude via Spring AI: structured-output prompt → InsightRecord
                                     ▼
                              insight.generated.v1 ──► notification-service (rich insight card)
                                                              │
                                                              ▼
                                                     Angular app (SSE/WebSocket + REST history)
```

Two-stage notification is deliberate: the price alert arrives in seconds; the LLM insight follows ~30–90s later as an enrichment. The user never waits on the LLM.

---

## 5. Threshold Rules (movement-detector)

Config-driven (Spring Cloud Config or per-service YAML), not hard-coded:

```yaml
movement:
  thresholds:
    LARGE: 2.0      # market cap rank 1–100 (AMFI classification)
    MID:   3.0      # rank 101–250
    SMALL: 4.5      # rank 251+
  volume-confirmation: false   # v2: require volumeRatio > 1.5
  cooldown: PER_DIRECTION_PER_DAY
```

Cap category comes from reference-data-service (cached locally, refreshed daily). AMFI republishes classification half-yearly — reference-data-service owns that ingestion.

---

## 6. LLM Integration (insight-service)

- **Spring AI `spring-ai-starter-model-anthropic`** with `ChatClient` and structured output mapped straight to the `InsightRecord` Java record. See [Spring AI Anthropic docs](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html).
- **Model strategy:** `claude-sonnet-5` default; consider Haiku for a cheap first-pass relevance filter over headlines, Sonnet for the final insight.
- **Prompt shape:** system prompt fixes role + JSON schema + explicit "if no credible cause, say UNKNOWN with LOW confidence — never speculate"; user message carries movement context + top N (≤10) filtered articles (title, snippet, source, timestamp).
- **Guardrails:** per-day token budget, retry with exponential backoff, on persistent failure emit `insight.failed.v1`. Persist full prompt + response for audit/eval.
- **Cost note:** 10 stocks × worst case all trigger daily ≈ 10 LLM calls/day. Negligible. Design still budgets because momentum trading multiplies call volume later.

---

## 7. Market Data & News Sources (India)

Pluggable `MarketDataProvider` SPI in market-data-service; swap providers without touching consumers.

| Phase | Prices | News |
|-------|--------|------|
| V1 (free) | Yahoo Finance (`.NS`/`.BO` suffixes) — 5-min delayed is fine for daily insights | Moneycontrol + Economic Times RSS, [NSE RSS feeds](https://www.nseindia.com/static/rss-feed) (corporate announcements — highest-signal source), NewsAPI free tier |
| V2 (reliable) | Broker API: Zerodha Kite Connect / Upstox / [ICICI Breeze](https://www.icicidirect.com/futures-and-options/api/breeze) (free with account) — also unlocks order placement later | Paid news API (Marketaux/Finnhub-style) if RSS coverage gaps hurt |

Caution: scraping nseindia.com directly is brittle (aggressive bot blocking) and against their ToS for automated use — prefer Yahoo Finance or a broker API. NSE's official real-time feed is paid.

---

## 8. Cross-Cutting Concerns

- **Auth:** JWT issued by gateway (Spring Security + a simple user store in watchlist-service for v1; Keycloak when multi-user matters).
- **Idempotency:** all consumers idempotent via `eventId` dedupe table/Redis set. Kafka gives at-least-once.
- **Error handling:** per-consumer DLQ topics (`<topic>.dlq`); alert on DLQ depth.
- **Observability:** Spring Boot Actuator + Micrometer → Prometheus/Grafana; correlation ID = `eventId` chain (`triggerTickEventId` → `movementEventId`) traces a movement end-to-end.
- **Local dev:** single `docker-compose.yml` — Kafka (KRaft, single broker), one Postgres with schema-per-service (cheaper locally; separate instances in prod), Redis, all services.
- **Contracts:** event schemas live in a shared `alphalens-events` module (plain Java records, no Spring deps) versioned independently. Avro/Schema Registry is deferred until >2 teams or >10 topics.

---

## 9. Angular Frontend (v1 scope)

Single app: login → watchlist management (search companies via reference-data through gateway) → live "Today's Movers" feed (SSE) → insight detail view (headline, summary, category chip, confidence badge, source links) → 90-day insight history per stock.

---

## 10. Phased Roadmap

| Phase | Deliverable |
|-------|-------------|
| **1** | docker-compose infra + reference-data-service (evolve existing code: JPA + Postgres + cap category) + watchlist-service + gateway. Angular: login + watchlist CRUD. |
| **2** | market-data-service (Yahoo provider) + movement-detector + Kafka topics + notification (SSE). Angular: movers feed. |
| **3** | insight-service (RSS ingestion + Spring AI/Claude) + insight UI + audit log. **← v1 complete** |
| **4** | Broker API integration (read-only: live prices, holdings). Volume-confirmation rules. |
| **5** | order-service + momentum-strategy-service (paper-trading mode first, mandatory), then live with human-in-the-loop approval. |

---

## 11. Key Risks

1. **Free data reliability** — Yahoo endpoints change without notice. Mitigate: provider SPI + health checks + broker API as planned upgrade.
2. **LLM hallucinated causality** — the biggest product risk. Mitigate: UNKNOWN-is-valid prompting, confidence field surfaced in UI, sources always linked, audit log enables spot-checking.
3. **News relevance for small caps** — thin coverage; NSE corporate announcements RSS is often the *only* signal. Weight it highest.
4. **Ops overhead of 7 services for a solo dev** — accepted trade-off per your call; docker-compose + shared parent POM + a service template keep it manageable.
5. **Future auto-trading is regulated territory** (SEBI algo-trading rules for retail) — paper-trade first; revisit compliance before Phase 5 goes live.
