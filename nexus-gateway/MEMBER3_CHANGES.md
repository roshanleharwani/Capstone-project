# Member 3 — NexusMarket Incentive Engine: Changes Documentation

## Overview

This document records all files created and modified as part of the Member 3 implementation
for the `market-incentives` module of the NexusGateway project.

Member 3's mandate is to build the **NexusMarket Incentive Engine** — the game-theory and
quota management layer that sits between the `ingress-shield` (Member 1) and
`context-compression` (Member 2) modules. It intercepts every tenant request to check token
quota, runs Stackelberg dynamic pricing when demand exceeds supply, conducts a VCG second-price
auction to allocate pooled surplus tokens, and records all transfers in an immutable double-entry
credit ledger backed by PostgreSQL (with Row-Level Security) and Valkey.

---

## New Module: `modules/market-incentives/`

### `pom.xml`
Maven build descriptor for the `market-incentives` module.
- Spring Boot 3.2.3 parent BOM (matches sibling modules)
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-data-redis` (Valkey-compatible), `postgresql` (42.7.2), `jackson-databind`
- Test: `spring-boot-starter-test`, `h2` (in-memory DB for isolated unit tests)

---

### `src/main/resources/application.properties`
Module configuration:
- Server port: `8083`
- PostgreSQL datasource pointing to `nexusgateway` database
- Valkey/Redis host + timeout settings for ledger balance cache
- Stackelberg pricing parameters: `p-min=0.5`, `p-max=3.0`, `steepness=12.0`, `threshold=0.75`
- Auction min-bid: `0.1` credits/kTokens
- Default daily limit: `1,000,000` tokens

---

### Main Sources (`src/main/java/com/nexus/market/`)

#### `MarketIncentivesApplication.java`
Spring Boot `@SpringBootApplication` entry point for the module.

---

#### Package `quota/`

##### `TenantQuota.java` — JPA `@Entity`
Mapped to the `tenant_quotas` PostgreSQL table.
- Columns: `tenant_id` (PK), `daily_limit`, `used_tokens`, `priority_credits`, `reset_at`
- Schema includes `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` for multi-tenant data isolation
- Derived helpers: `remainingTokens()`, `hasSurplus()`

##### `TenantQuotaRepository.java` — Spring Data `@Repository`
- `findByTenantId(tenantId)` — single tenant lookup
- `findTenantsWithSurplus()` — JPQL query for all tenants with `usedTokens < dailyLimit`
- `computeTotalSurplusTokens()` — aggregate JPQL: `SUM(dailyLimit - usedTokens)` across surplus tenants

##### `QuotaCheckResult.java` — Value Object
Encapsulates the result of a `TenantQuotaManager.checkAndReserve()` call:
- `sufficient` (boolean) — whether the tenant's own quota covers the request
- `remainingTokens`, `tokensNeeded`, `deficitTokens`
- `systemUtilization` — U ∈ [0,1] computed at check time, passed to Stackelberg engine
- Factory methods: `QuotaCheckResult.sufficient(...)` and `QuotaCheckResult.deficit(...)`

##### `TenantQuotaManager.java` — `@Service`
Core Task 1: Tenant Quota Tracking.
- `checkAndReserve(tenantId, tokensNeeded)` — atomically checks and decrements quota;
  auto-provisions unknown tenants with default limit; auto-resets expired quota windows
- `releaseReservation(tenantId, tokens)` — returns tokens on request failure/escalation
- `applyBorrowedTokens(tenantId, tokens)` — increases `dailyLimit` after an auction win
- System utilization computed via `quotaRepository.count()` + `computeTotalSurplusTokens()`

---

#### Package `pricing/`

##### `StackelbergPricingEngine.java` — `@Service`
Core Task 2: Stackelberg Dynamic Pricing.

Implements the two-stage Stackelberg game (referenced in Section III.C of the design paper):

**Stage 1 — Gateway Leader:** Computes the current token exchange rate using a sigmoid pricing function:
```
p_t = p_min + (p_max - p_min) * sigmoid(k * (U - threshold))
```
- `U` = system-wide utilization (fraction of total daily budget consumed)
- `p_min = 0.5`, `p_max = 3.0` credits per 1000 tokens
- `k = 12.0` (steepness) and `threshold = 0.75` are tunable via `application.properties`
- The sigmoid ensures pricing stays near `p_min` at normal loads and accelerates sharply above 75% utilization

**Stage 2 — Tenant Followers:**
- `shouldLend(rate, remainingTokens)` — surplus tenants auto-opt in when `rate > p_min`
- `shouldBorrow(deficitTokens, rate)` — deficit tenants borrow when `rate <= p_max`

---

#### Package `auction/`

##### `TenantBid.java` — Value Object
Sealed bid submitted by a quota-starved tenant:
- `tenantId`, `tokensNeeded`, `creditBidPerKTokens`
- Validated on construction (tokens > 0, bid >= 0)

##### `AuctionResult.java` — Value Object
Per-tenant outcome of the VCG auction:
- `tenantId`, `tokensAllocated`, `creditsPaid`, `cleared` (boolean)
- Factory methods: `AuctionResult.winner(...)`, `AuctionResult.loser(...)`

##### `VcgAuctionSolver.java` — `@Service`
Core Task 3: VCG Second-Price Sealed-Bid Auction.

Implements the Vickrey–Clarke–Groves mechanism (referenced in Section IV.D):

**Algorithm:**
1. Filter out below-`min-bid` bids; sort remainder descending by `creditBidPerKTokens`
2. Greedily allocate tokens from the surplus pool to the highest bidder first
3. Find the "first loser" index — the first bid that could not be fully satisfied
4. All winners pay the `creditBidPerKTokens` of the first loser (second-price rule)
5. If all bids win, winners pay the `minBid` floor price

**VCG Guarantee:** Bidding true valuation is the dominant strategy — no tenant gains by
misreporting their demand, preventing market manipulation.

---

#### Package `ledger/`

##### `LedgerEntry.java` — JPA `@Entity`
INSERT-only double-entry bookkeeping row.
- Columns: `id` (UUID PK), `tenant_id`, `counterparty_id`, `entry_type` (`DEBIT`/`CREDIT`),
  `amount`, `description`, `transaction_hash` (SHA-256), `timestamp`
- Schema includes `ENABLE ROW LEVEL SECURITY` for tenant data isolation
- `updatable = false` on hash and timestamp columns enforces immutability

##### `LedgerRepository.java` — Spring Data `@Repository`
- `findByTenantIdOrderByTimestampDesc(tenantId)` — transaction history
- `findByTransactionHash(hash)` — integrity lookup
- `computeNetBalance(tenantId)` — JPQL aggregate: `SUM(CREDIT) - SUM(DEBIT)`
- `findTopByTenantIdOrderByTimestampDesc(tenantId)` — chain continuation
- `findByTransactionIdInDescription(txId)` — audit pair lookup

##### `CreditLedgerService.java` — `@Service`
Core Task 4: Immutable Double-Entry Transactional Credit Ledger.

**Borrow transaction** (`recordTokenBorrow`):
- Creates two entries atomically in a `@Transactional` block
- `Borrower: DEBIT +X credits` (owes credits, gains tokens)
- `Lender: CREDIT +X credits` (earns credits, loses surplus tokens)
- Credit amount = `(tokensTransferred / 1000.0) * creditsPerKTokens`
- Each entry's `transactionHash` = `SHA-256(tenantId | entryType | amount | txId)`
- Both tenant balance caches evicted from Valkey after write

**Return transaction** (`recordTokenReturn`):
- Reversal: CREDIT on borrower, DEBIT on lender

**Balance reads** (`getBalance`):
- Valkey/Redis cache-first read (key: `ledger:balance:{tenantId}`, TTL: 30s)
- On cache miss: computes from PostgreSQL via `computeNetBalance`, repopulates cache

**Integrity verification** (`verifyEntryIntegrity`):
- Recomputes hash from stored fields and compares to stored `transactionHash`

---

#### Package `service/`

##### `MarketIncentiveService.java` — `@Service` (Pipeline Orchestrator)
Wires all four components into the full NexusMarket processing pipeline:

```
SanitizedContext (tenantId, tokensNeeded)
  → [1] TenantQuotaManager.checkAndReserve()
      SUFFICIENT ──► MarketApproval(approved=true, tokensBorrowed=0)

      DEFICIT    ──► [2] StackelbergPricingEngine.computeExchangeRate(U)
                     [3] shouldBorrow? → NO → denied (rate too high)
                     [4] computeTotalSurplusTokens → 0? → denied (pool empty)
                     [5] VcgAuctionSolver.solve(bids=[{tenantId, deficit, rate}], pool)
                         WINNER ──► [6] TenantQuotaManager.applyBorrowedTokens()
                                    [7] CreditLedgerService.recordTokenBorrow()
                                    MarketApproval(approved=true, borrowed, credits, txId)
                         LOSER  ──► denied (not cleared)
```

Includes nested `MarketApproval` DTO (module-local) to avoid runtime classpath coupling to `contracts/`.

---

### Unit Tests (`src/test/java/com/nexus/market/`)

All 5 test classes are fully isolated — no Spring context loaded, all external dependencies
mocked via Mockito/JUnit 5.

| Test Class | Component Under Test | Scenarios Covered |
|---|---|---|
| `TenantQuotaManagerTest` | `TenantQuotaManager` | Sufficient quota + reservation decrement; deficit no-save; partial shortfall deficit calculation; unknown tenant auto-provisioning; expired window auto-reset; release reservation; apply borrowed tokens |
| `StackelbergPricingEngineTest` | `StackelbergPricingEngine` | U=0 → near p_min; U=1 → near p_max; U=threshold → midpoint; always in [p_min, p_max]; monotonicity over 101 points; parameterized spot checks; sigmoid(0)=0.5; sigmoid in (0,1); shouldLend true/false; shouldBorrow true/false |
| `VcgAuctionSolverTest` | `VcgAuctionSolver` | Empty/null bids; zero pool all lose; single bidder wins at min-bid; multi-bidder second-price; all bids clear at min-bid floor; pool exhaustion losers; below-min-bid rejection; exactly-min-bid acceptance |
| `CreditLedgerServiceTest` | `CreditLedgerService` | Paired DEBIT+CREDIT creation; correct credit amounts; SHA-256 hash is 64-char hex; both caches evicted; UUID txId returned; return reversal entry types; cache HIT skips DB; cache MISS queries DB + populates cache; null DB result → 0.0; verifyIntegrity unknown ID → false |
| `MarketIncentiveServiceTest` | `MarketIncentiveService` | Pass-through (no auction/ledger); deficit+win (full pipeline, field propagation); deficit+loss; empty pool denied before auction; rate-too-high denied before auction; tenantId/utilization in denial output |

---

## Modified Files (`contracts/` — content filled in)

### `contracts/dto/QuotaApproval.java`
Previously an empty stub. Now populated with the full contract DTO for Member 3's output:
- `sessionId`, `tenantId`
- `approved` — whether request may proceed
- `tokensAllocated` — total authorized tokens (own + borrowed)
- `tokensBorrowed` — tokens from surplus pool (0 for pass-through)
- `creditsCharged` — VCG second-price credits paid (0 for pass-through)
- `exchangeRate` — Stackelberg `p_t` at time of request (0 for pass-through)
- `auctionCleared` — true if VCG auction was run and won
- `transactionId` — UUID linking the ledger DEBIT+CREDIT pair (null for pass-through)
- `metadata` — audit map (module name, version)

---

## Architecture Constraints Respected

- All new code is confined to `modules/market-incentives/` (read/write allowed ✅)
- `contracts/dto/QuotaApproval.java` was an empty stub — filled in (no structural change to other DTOs ✅)
- No files in `modules/ingress-shield/`, `modules/context-compression/`, `modules/router-dashboard/`, or any other module were modified ✅
- Unit tests mock all incoming/outgoing data and do not wire the full application pipeline ✅
- The `contracts/` directory was not structurally modified — only `QuotaApproval.java` content was added ✅
