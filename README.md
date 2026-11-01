# Metro Smartcard

![CI](https://github.com/AmruthaVarshini111/metro-smartcard/actions/workflows/ci.yml/badge.svg)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)

**A metro route, fare and smartcard-wallet backend where retried requests never double-charge and concurrent taps never corrupt a balance.**

**[Live demo](https://metro-smartcard.onrender.com)** | [API docs (Swagger)](https://metro-smartcard.onrender.com/swagger-ui.html) | [Source on GitHub](https://github.com/AmruthaVarshini111/metro-smartcard)

> The demo runs on free hosting, so the first request after a quiet period can take about a minute to wake up.

---

## Try it in two minutes

Open the [live demo](https://metro-smartcard.onrender.com), click an endpoint, then **Try it out** and **Execute**:

1. `POST /api/cards` issues a card. Copy the `id` from the response.
2. `POST /api/cards/{id}/top-ups` with header `Idempotency-Key: tu-1` and body `{"amountMinor": 10000}` adds Rs 100.
3. `POST /api/cards/{id}/taps` with key `in-1` and body `{"type": "IN", "stationId": "B01"}` opens a journey.
4. The same endpoint with key `out-1` and body `{"type": "OUT", "stationId": "B03"}` closes it and charges the fare.
5. Send step 4 again with the **same key**. Nothing extra is charged.
6. `GET /api/cards/{id}` shows the balance, trip history and every ledger entry.

Example response to step 4 (off-peak; amounts are in paise, so 1800 = Rs 18.00):

```json
{
  "type": "OUT",
  "stationId": "B03",
  "chargedMinor": 1800,
  "penaltyApplied": false,
  "balanceMinor": 8200,
  "message": "Fare charged"
}
```

---

## What it does

- **Plans routes** across a multi-line network, either fastest or with fewest interchanges.
- **Prices journeys** by distance slab, with peak and off-peak pricing, weekend rules and a daily fare cap.
- **Runs a smartcard wallet** where every balance change is an immutable ledger entry.
- **Enforces gate rules**: minimum entry balance, penalties for a missing tap-in or tap-out, and trip history including incomplete trips.
- **Exposes a REST API** with generated OpenAPI docs and structured error responses.

---

## What this project demonstrates

| Skill | Where to look |
|---|---|
| Algorithms and data structures | `RoutePlanner`: Dijkstra over a graph, with interchanges modelled as explicit edges so "fewest interchanges" is a real, testable alternative to "fastest" |
| Backend correctness | `SmartCard`, `TapService`: idempotent operations, per-card locking, append-only ledger |
| Concurrency | `TapServiceTest`: 16 threads retry the same tap-out and the card is charged exactly once |
| Clean architecture | `domain/` has no framework dependencies; `api/` only wires and exposes it |
| API design | `Idempotency-Key` header, RFC 9457 problem responses with stable error codes, OpenAPI docs |
| Testing | 30 unit and concurrency tests covering boundaries, penalties, caps and failure paths |
| DevOps | Multi-stage Dockerfile, GitHub Actions CI on every push, automatic deploys to Render |

---

## Engineering highlights

**Retries are safe.** Gates and mobile apps retry on bad networks. Every top-up and tap carries a client-supplied idempotency key. A repeat returns the original result and charges nothing. Reusing a key for a different request is rejected with `IDEMPOTENCY_KEY_REUSED`.

**Concurrent requests cannot race.** Operations on one card are serialised under a per-card lock, so the daily-cap check and the balance update happen as one step. Different cards never block each other.

**Money is exact.** All amounts are integers in minor units (paise), never floating point, so fares and balances always add up.

**The wallet is auditable.** Nothing edits a balance directly. The balance is the sum of an append-only ledger, and each entry records its type, amount, time and reason.

**Rejections leave no mess.** Rules are validated before anything is changed, so a refused tap-in never leaves a half-applied penalty behind.

**Business rules live in one place.** Fare slabs, peak windows, discount, cap, penalty and minimum balance are all in `FarePolicy`, one immutable value that is validated on construction.

---

## Business rules

| Rule | Value |
|---|---|
| Fare | Distance slabs from Rs 10 to Rs 60 |
| Off-peak discount | 10% outside peak hours; weekends are always off-peak |
| Peak hours (weekdays) | 08:00 to 10:00 and 17:00 to 19:00, decided by tap-in time |
| Daily cap | Rs 100 in fares per calendar day (Asia/Kolkata); penalties are not counted |
| Minimum balance to enter | Rs 20 |
| Tap-in with an open trip | Previous trip marked incomplete, Rs 50 penalty |
| Tap-out with no tap-in | Rs 50 penalty |
| Fare basis | Fastest route between entry and exit stations |

---

## Architecture

```
  HTTP client
      |
      v
  MetroController  ------>  ApiExceptionHandler
      |                      (error code -> HTTP status)
      v
  TapService  ----------------+-----------------+
      |                       |                 |
      v                       v                 v
  RoutePlanner           FareCalculator    CardRepository
  (Dijkstra)             (slabs, peak,     (port; in-memory
      |                   discount)         adapter today)
      v                                         |
  MetroNetwork                                  v
  (stations + edges)                       SmartCard
                                           (ledger, trips,
                                            idempotency)
```

```
src/main/java/com/metro/
  domain/   routing, fares, ledger wallet, tap rules (framework-free)
  api/      Spring wiring, REST controller, error mapping
src/test/java/com/metro/domain/   unit and concurrency tests
```

---

## API

Amounts are in minor units. State-changing calls require an `Idempotency-Key` header.

| Method and path | Purpose |
|---|---|
| `GET /api/stations` | List stations |
| `GET /api/route?from=B01&to=R01&criterion=FASTEST` | Route, totals and quoted fare (`FASTEST` or `FEWEST_INTERCHANGES`) |
| `POST /api/cards` | Issue a card |
| `GET /api/cards/{id}` | Balance, open trip, trip history, ledger |
| `POST /api/cards/{id}/top-ups` | Body `{"amountMinor": 10000}` |
| `POST /api/cards/{id}/taps` | Body `{"type": "IN" or "OUT", "stationId": "B01"}` |

Errors are RFC 9457 problem responses with a stable `code` field, for example `INSUFFICIENT_BALANCE`, `UNKNOWN_STATION`, `NO_ROUTE`.

---

## Testing

30 tests, run on every push by GitHub Actions. They cover:

- Routing: same-line and cross-line routes, symmetry, fastest vs fewest interchanges, unknown and disconnected stations
- Fares: slab boundaries, peak window edges (start inclusive, end exclusive), weekends, invalid policies
- Wallet and gates: normal trips, peak vs off-peak, missing taps, penalties, minimum balance, daily cap and its reset, invalid times
- Idempotency: repeated taps and top-ups, conflicting key reuse
- Concurrency: 16 parallel retries of one tap-out charge once; 64 parallel top-ups all apply

---

## Tech stack

Java 21, Spring Boot 3.5, Maven, JUnit 5, springdoc OpenAPI, Docker, GitHub Actions, Render.

---

## Run locally

Requires Java 21 and Maven.

```bash
git clone https://github.com/AmruthaVarshini111/metro-smartcard.git
cd metro-smartcard
mvn verify              # build and run all tests
mvn spring-boot:run     # API at http://localhost:8080 (docs at /swagger-ui.html)
```

With Docker:

```bash
docker build -t metro-smartcard .
docker run -p 8080:8080 metro-smartcard
```

---

## Limitations and roadmap

Known limitations, stated plainly:

- Cards are stored **in memory**, so data resets when the app restarts
- The station network is a small **fictional sample** (3 lines, 21 stations)
- No authentication yet
- Fares are always charged for the fastest route, not the route the passenger actually took

Planned next:

- [ ] PostgreSQL persistence behind the existing `CardRepository` port, with Flyway migrations, a unique constraint on `(card_id, idempotency_key)` and a row lock on the card
- [ ] API-level tests (MockMvc) and database tests (Testcontainers)
- [ ] Load a real network from public transit data (GTFS)
- [ ] Authentication with commuter and admin roles
- [ ] Load test report (taps per second)

---

## About

Built by Amrutha Varshini ([GitHub](https://github.com/AmruthaVarshini111)).
