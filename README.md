# Metro Smartcard

![CI](https://github.com/AmruthaVarshini111/metro-smartcard/actions/workflows/ci.yml/badge.svg)

A metro route, fare and smartcard-wallet engine in Java 21, exposed through a Spring Boot REST API.

Given a station network, it plans routes (fastest or fewest interchanges), prices journeys with distance slabs, peak and off-peak rules and a daily cap, and runs a smartcard wallet where every balance change is an immutable ledger entry and every gate tap is safe to retry.

The network in `SampleNetwork` is fictional (three lines, 21 stations, three interchanges). The engine only depends on the `MetroNetwork` API, so a real network can be loaded behind it.

## Why it is built this way

| Decision | Reason |
|---|---|
| **Graph + Dijkstra, interchanges as explicit edges** | Interchanges can be counted and penalised, so "fewest interchanges" is a real, testable alternative to "fastest". |
| **Money as integer minor units (paise)** | No floating-point drift; balances and fares always add up exactly. |
| **Append-only ledger, balance derived** | Nothing edits a balance. Every rupee in or out has a row, a timestamp and a reason, which makes disputes and audits straightforward. |
| **Idempotency key on every top-up and tap** | Gates and mobile apps retry. A retry returns the original result and never charges twice. Reusing a key for a different request is rejected. |
| **Per-card locking** | Concurrent taps on one card are serialised, so the daily cap and balance checks cannot race. Different cards do not block each other. |
| **Validate, then mutate** | A rejected tap-in leaves no partial state (no half-applied penalty). |
| **Framework-free domain package** | Business rules have no Spring or database dependency, so they are fast to test and easy to move. Spring only wires and exposes them. |

## Rules implemented

- Fare = distance slab (Rs 10 to Rs 60), 10% off outside peak. Peak is 08:00-10:00 and 17:00-19:00 on weekdays; weekends are always off-peak. Peak status is decided by tap-in time.
- Daily cap of Rs 100 on fares, per local calendar day (Asia/Kolkata). Penalties do not count toward the cap.
- Tap-in needs a balance of at least Rs 20.
- Tap-in with a trip still open: the old trip is marked incomplete and a Rs 50 penalty is charged.
- Tap-out with no tap-in: Rs 50 penalty.
- Fare is charged for the fastest route between entry and exit stations.

All amounts live in `FarePolicy.defaults()` and are easy to change.

## API

Money is in minor units (1000 = Rs 10). State-changing calls require an `Idempotency-Key` header.

| Method and path | Purpose |
|---|---|
| `GET /api/stations` | List stations |
| `GET /api/route?from=B01&to=R01&criterion=FASTEST` | Route, totals and quoted fare (`criterion` is `FASTEST` or `FEWEST_INTERCHANGES`) |
| `POST /api/cards` | Issue a card |
| `GET /api/cards/{id}` | Balance, open trip, trip history, ledger |
| `POST /api/cards/{id}/top-ups` | Body `{"amountMinor": 10000}` |
| `POST /api/cards/{id}/taps` | Body `{"type": "IN", "stationId": "B01"}` or `"OUT"` |

Errors are RFC 9457 problem responses with a stable `code` field (for example `INSUFFICIENT_BALANCE`, `UNKNOWN_STATION`).

Interactive docs are served at `/swagger-ui.html`.

```bash
mvn spring-boot:run

curl -s -X POST localhost:8080/api/cards                      # note the id
curl -s -X POST localhost:8080/api/cards/<id>/top-ups \
  -H 'Idempotency-Key: tu-1' -H 'Content-Type: application/json' -d '{"amountMinor":10000}'
curl -s -X POST localhost:8080/api/cards/<id>/taps \
  -H 'Idempotency-Key: in-1' -H 'Content-Type: application/json' -d '{"type":"IN","stationId":"B01"}'
curl -s -X POST localhost:8080/api/cards/<id>/taps \
  -H 'Idempotency-Key: out-1' -H 'Content-Type: application/json' -d '{"type":"OUT","stationId":"B03"}'
```

## Run and test

Requires Java 21 and Maven.

```bash
mvn verify                      # build + all tests
mvn spring-boot:run             # start the API on :8080
docker build -t metro-smartcard . && docker run -p 8080:8080 metro-smartcard
```

Tests cover routing (including the fastest vs fewest-interchange trade-off), fare slab boundaries and peak-window edges, the full tap lifecycle, daily cap, penalties, idempotent retries, and 16-thread concurrency on a single card.

## Layout

```
src/main/java/com/metro/
  domain/   routing, fares, ledger wallet, tap rules (no framework dependencies)
  api/      Spring wiring, REST controller, error mapping
src/test/java/com/metro/domain/   unit and concurrency tests
```

## Roadmap

Not built yet:

- [ ] PostgreSQL persistence (JPA adapter behind `CardRepository`, Flyway migrations, unique constraint on `(card_id, idempotency_key)`, row lock on the card)
- [ ] API-level tests (MockMvc) and database tests (Testcontainers)
- [ ] Load real network data (e.g. GTFS) instead of the sample
- [ ] Authentication with commuter and admin roles; admin endpoints for fares and stations
- [ ] Web front end: route planner, card dashboard, admin page
- [ ] Load test report (taps per second) and a hosted demo
