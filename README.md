![Build Status](https://github.com/mv78/PurchaseTx/actions/workflows/workflow.yml/badge.svg)

# PurchaseTx

A REST API for storing purchase transactions and retrieving them converted to a target currency using live exchange rates from the US Treasury.

Built with Spring Boot 4 / Jackson 3.

---

## What it does

1. Accept a purchase (description, date, USD amount) and persist it.
2. On demand, convert a stored purchase to any currency supported by the [US Treasury Rates of Exchange API](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange/). The rate used must be within 6 months of the purchase date; otherwise the request is rejected.

---

## API

```
POST   /api/v1/purchases
GET    /api/v1/purchases/{id}
GET    /api/v1/purchases/{id}/converted?country=Canada&currency=Dollar
```

**Create purchase**
```json
POST /api/v1/purchases
{
  "description": "Office supplies",
  "transactionDate": "2024-08-15",
  "purchaseAmountUsd": 123.45
}
```
Returns `201 Created` with a `Location` header.

**Convert purchase**
```
GET /api/v1/purchases/{id}/converted?country=Canada&currency=Dollar
```
Returns the original purchase plus `exchangeRate`, `exchangeRateDate`, and `convertedAmount` (rounded half-up to 2 decimal places).

All error responses follow [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457).

---

## Architecture

```
┌─────────────┐     POST/GET      ┌──────────────────────────────────────────┐
│   Client    │ ───────────────►  │              Spring MVC                  │
└─────────────┘                   │                                          │
                                  │  PurchaseController   ConversionController│
                                  └──────────┬───────────────────┬───────────┘
                                             │                   │
                                    ┌────────▼──────┐   ┌───────▼──────────────┐
                                    │ PurchaseService│   │ CurrencyConversion   │
                                    │               │   │ Service              │
                                    └────────┬──────┘   └───────┬──────────────┘
                                             │                   │
                                    ┌────────▼──────┐   ┌───────▼──────────────┐
                                    │  H2 (dev/test)│   │ ExchangeRateService  │
                                    │  PG (prod)    │   │  (Caffeine cache)    │
                                    │  JPA/Flyway   │   │                      │
                                    └───────────────┘   └───────┬──────────────┘
                                                                 │
                                                        ┌────────▼──────────────┐
                                                        │  TreasuryRatesClient  │
                                                        │  (RestClient)         │
                                                        └────────┬──────────────┘
                                                                 │  HTTPS
                                                        ┌────────▼──────────────┐
                                                        │  fiscaldata.treasury  │
                                                        │       .gov            │
                                                        └───────────────────────┘
```

**Packages**

| Package | Responsibility |
|---|---|
| `purchase` | Entity, JPA repository, service, REST controller |
| `convertion` | Conversion controller + service |
| `currency.client` | Treasury API client, response DTOs, exchange rate domain model |
| `error` | Global exception handler, custom exceptions |
| `web` | Correlation ID filter |
| `config` | Clock bean (UTC), OpenAPI metadata |

**Key design decisions**
- Three Spring profiles: `dev` (H2 file, default), `test` (H2 in-memory, test suite), `prod` (PostgreSQL + Redis via env vars).
- UTC timezone enforced at JVM, JDBC, and Hibernate layers.
- Schema managed by Flyway; Hibernate runs in `validate` mode only.
- Exchange rates cached for 24 hours — Caffeine (in-process) in `dev`, Redis in `prod`.
- Correlation IDs (`X-Correlation-ID`) propagated via MDC through every request; auto-generated if absent.
- Amount rounding is `HALF_UP` to 2 decimal places.

---

## Known Limitations & Assumptions

### No idempotency support on POST
`POST /api/v1/purchases` is not idempotent. Each call creates a new record with a new ID regardless of whether an identical payload was submitted moments before (e.g. a client retry on a network timeout). In production this means a failed-but-processed request can result in duplicate rows. A production-ready solution would accept a caller-supplied `Idempotency-Key` header and persist a short-lived deduplication record, but that is not implemented. Callers must handle retries at the application level.

### IDs are UUIDv4 — UUIDv7 recommended for production
Purchase IDs are generated by Hibernate's `GenerationType.UUID`, which produces random UUIDv4 values. Random UUIDs scatter inserts across the B-tree index, causing page splits and degraded write throughput at scale. UUIDv7 (time-ordered UUID) would preserve insertion order and significantly improve index locality. Switching requires configuring Hibernate's `@UuidGenerator(style = TIME)` annotation and a Flyway migration to change the column default; this trade-off is accepted for the current scope.

### Purchase date has no timestamp
`transactionDate` is stored as a date only (`yyyy-MM-dd`). Two purchases with the same description, date, and amount are indistinguishable — no deduplication logic exists. Callers are responsible for avoiding duplicate submissions.

### 6-month window is enforced in the service layer
The Treasury API may return a rate whose record date falls outside the 6-month window relative to the purchase date. The service explicitly checks this after the rate is retrieved and rejects conversions where no qualifying rate exists, even when the API returns a result.

### Country and currency inputs are not normalised
The `country` and `currency` query parameters passed to `GET /api/v1/purchases/{id}/converted` are forwarded to the Treasury API lookup as-is, without being converted to a canonical form (e.g. title case). This has two consequences:
- A lookup for `canada`/`dollar` will fail even though `Canada`/`Dollar` would succeed, because the Treasury API is case-sensitive.
- Cache entries are keyed on the raw input strings, so `Canada/Dollar`, `canada/dollar`, and `CANADA/DOLLAR` are treated as three distinct cache entries, inflating cache size and bypassing hits.

### Cache key uses `-` as a delimiter — collision risk with hyphenated country names
Exchange rate cache keys are constructed by joining country and currency with a `-` separator (e.g. `Canada-Dollar`). A country name that itself contains a hyphen (e.g. `Bosnia-Herzegovina`) will produce a key that cannot be unambiguously split, creating a potential collision with a different country/currency pair that happens to tokenise to the same string.

---

## Input validation

### POST /api/v1/purchases

```
Incoming JSON body
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│  Content-Type check                                         │
│  Must be application/json                  → 415 otherwise  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  JSON parsing + field deserialization                        │
│                                                             │
│  transactionDate  strict ISO-8601 only (yyyy-MM-dd)         │
│    • US-style (08/15/2024)     → 400 Malformed request      │
│    • datetime (2024-08-15T...) → 400 Malformed request      │
│    • impossible date (2024-13-45) → 400 Malformed request   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Bean Validation (@Valid)                                   │
│                                                             │
│  description        @NotBlank                               │
│                     @Size(max = 50)                         │
│                                                             │
│  transactionDate    @NotNull                                │
│                     @PastOrPresent (no future dates)        │
│                                                             │
│  purchaseAmountUsd  @NotNull                                │
│                     @DecimalMin("0.01") (must be positive)  │
│                     @Digits(integer=13, fraction=2)         │
│                       (at most 2 decimal places)            │
│                                                 → 400 on fail│
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
                      ✓ accepted → persist → 201 Created
```

### GET /api/v1/purchases/{id}/converted

```
Query parameters: country, currency (both required)
Path variable:    id (UUID)
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│  Path variable type check                                   │
│  id must be a valid UUID          → 400 Invalid parameter   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Required parameter check                                   │
│  country or currency absent       → 400 Missing parameter   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Purchase lookup                                            │
│  id not found in DB               → 404 Purchase not found  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Exchange rate lookup (Treasury API)                        │
│  API unreachable / 5xx            → 502 Upstream unavailable│
│  No rate within 6 months of       → 422 Exchange rate       │
│  purchase date                         not available        │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
                  ✓ accepted → convert (HALF_UP, 2dp) → 200 OK
```

All error bodies follow RFC 9457 (`title`, `detail`, `status`).

---

## API documentation

When the application is running, interactive docs are available via Swagger UI:

- **Swagger UI** → `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON** → `http://localhost:8080/v3/api-docs`

---

## Running locally

Copy `.env.example` to `.env` and adjust as needed, then:

```bash
./mvnw spring-boot:run
```

The default profile is `dev` — no configuration required. H2 console is available at  
`http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/purchases`).

### Profiles

| Profile | Database | Cache | How to activate |
|---|---|---|---|
| `dev` (default) | H2 file-based | Caffeine (in-process) | run without any profile |
| `test` | H2 in-memory | Caffeine (in-process) | activated automatically by the test suite |
| `prod` | PostgreSQL | Redis | `SPRING_PROFILES_ACTIVE=prod` |

### Production environment variables

Required when running with `SPRING_PROFILES_ACTIVE=prod`:

```
DB_URL=jdbc:postgresql://host:5432/dbname
DB_USERNAME=your_user
DB_PASSWORD=your_password

REDIS_HOST=your-redis-host   # default: redis (matches docker-compose service name)
REDIS_PORT=6379              # default: 6379
REDIS_PASSWORD=              # default: empty
```

See `.env.example` for a full template.

---

## Docker

### Docker Compose (recommended for local development)

Starts the application and a PostgreSQL 17 container together, networked automatically.

```bash
# 1. create your .env from the example (only needed once)
cp .env.example .env
# edit .env and set DB_USERNAME and DB_PASSWORD

# 2. build the app image and start everything
docker compose up

# rebuild after code changes
docker compose up --build

# run in the background
docker compose up -d

# view logs
docker compose logs -f

# stop (keeps database volume)
docker compose down

# stop and wipe the database
docker compose down -v
```

Once running:
- **API** → `http://localhost:8080`
- **Swagger UI** → `http://localhost:8080/swagger-ui.html`
- **Health** → `http://localhost:8080/actuator/health`

Connect to PostgreSQL directly:
```bash
docker compose exec postgres psql -U purchasetx_user -d purchasetx
```

### Docker image only (without Compose)

Build and tag the image:

```bash
# Tags as  purchasetx:<version>  and  purchasetx:<git-sha>
./mvnw package -Pdocker

# Push to a registry
./mvnw package -Pdocker -Ddocker.registry=ghcr.io/myorg/
```

Run against an external database:

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://host:5432/purchasetx \
  -e DB_USERNAME=your_user \
  -e DB_PASSWORD=your_password \
  --memory=512m \
  purchasetx:1.0.0
```

### CI

The GitHub Actions workflow builds and pushes the image to `ghcr.io` automatically on every push to `main`:

```
ghcr.io/mv78/purchasetx:<version>
ghcr.io/mv78/purchasetx:<git-sha>
```

---

## Tests

```bash
./mvnw test
```

Three layers:
- Unit tests with Mockito (`PurchaseServiceTest`, `CurrencyConversionServiceTest`)
- MVC slice tests with `@WebMvcTest` (`PurchaseControllerTest`)
- Full integration tests with WireMock stubbing the Treasury API (`PurchaseConversionIntegrationTest`, `TreasuryRatesClientTest`)

---

## Stack

- Java 21
- Spring Boot 4 (Spring Framework 7, Jackson 3)
- PostgreSQL 17 (prod) / H2 (dev, test) + JPA + Flyway
- Redis (prod) / Caffeine (dev, test)
- springdoc-openapi (Swagger UI)
- Docker + Docker Compose
- WireMock (tests)
