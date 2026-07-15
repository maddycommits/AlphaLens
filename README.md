# AlphaLens — Architecture Design Document

**Version:** 0.1 (draft) · **Date:** 2026-07-15 · **Author:** Maddy
**Stack:** Spring Boot 3.5 / Java 21 · Angular · Apache Kafka · PostgreSQL · Spring AI (Anthropic Claude) · Docker Compose

---

## 1. Overview

AlphaLens is an LLM-powered stock analysis platform for Indian markets (NSE/BSE).

**V1 goal:** A user maintains a watchlist (~10 stocks). Every trading day, the system detects price movements above a market-cap-relative threshold (large cap > 2%, mid cap > 3%, small cap > 4–5%), fetches related news, and uses Claude to explain *why* the stock moved (earnings, order wins, regulatory action, block deals, sector moves, etc.).

**Future goals (must not require re-architecture):** order placement via broker APIs and automated momentum trading. This is why the system is event-driven from day one — new capabilities are added by attaching new consumers to existing Kafka topics, not by modifying existing services.

<img width="1781" height="1343" alt="architecture" src="https://github.com/user-attachments/assets/69c7c4c4-feca-4023-936f-8ed7f3760e53" />
