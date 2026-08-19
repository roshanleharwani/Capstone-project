# Member 2 — Context Compression Module: Changes Documentation

## Overview
This document records all files created and modified as part of the Member 2 implementation
for the `context-compression` module of the NexusGateway project.

---

## New Module: `modules/context-compression/`

### `pom.xml`
Maven build descriptor for the `context-compression` module.
- Spring Boot 3.2.3 parent BOM
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql` (42.7.2), `pgvector` (0.1.6), `jackson-databind`
- Test dependencies: `spring-boot-starter-test`, `h2` (in-memory DB for unit tests)

---

### Main Sources (`src/main/java/com/nexus/compression/`)

#### `ContextCompressionApplication.java`
Spring Boot `@SpringBootApplication` entry point for the module.

---

#### `repository/ChatHistoryEntry.java`
JPA `@Entity` mapped to the `chat_history` PostgreSQL table.
- Columns: `id`, `session_id`, `tenant_id`, `role`, `content`, `embedding` (stored as CSV string for pgvector portability), `created_at`, `turn_index`
- The `embedding` column is designed to hold a `vector(1536)` in production via pgvector's native type.

#### `repository/ChatHistoryRepository.java`
Spring Data JPA `@Repository` interface with two native SQL queries:
- `findRecentBySessionId(sessionId, limit)` — Recency Window: fetches the last N turns ordered by `turn_index DESC`
- `findTopKSimilar(sessionId, queryEmbedding, topK, excludeIds)` — Top-K semantic search using pgvector's `<=>` cosine distance operator
- `findBySessionIdOrderByTurnIndexAsc(sessionId)` — full session history in chronological order
- `countBySessionId(sessionId)` — used to assign sequential `turn_index` values

#### `repository/VectorStoreRepository.java`
**Semantic Shadow Cache** — Hybrid Retrieval Engine.

Implements the two-phase hybrid retrieval strategy:
1. **Recency Window** (`RECENCY_WINDOW = 6`): always includes the 6 most recent turns for temporal locality.
2. **Top-K MMR** (`TOP_K_CANDIDATES = 20`, `MMR_FINAL_K = 4`): retrieves 20 semantic candidates via pgvector cosine similarity, then applies **Maximal Marginal Relevance (MMR)** re-ranking to select 4 diverse, relevant turns.

MMR formula: `score = λ * sim(doc, query) - (1-λ) * max_sim(doc, selected)` with `λ = 0.6`.

Public methods:
- `storeTurn(sessionId, tenantId, role, content, embedding)` — persists a new chat turn
- `hybridRetrieve(sessionId, queryEmbedding)` — returns merged recency + MMR results

---

#### `service/PromptStratifier.java`
**Prompt Stratifier** — separates the prompt into two non-overlapping layers:

| Layer | Roles | Behaviour |
|---|---|---|
| Static Prefix | `system`, `tool`, `function` | Never compressed; token-budget-fixed |
| Dynamic Payload | `user`, `assistant` | Target for LLMLingua-2 compression |

Returns a `StratifiedPrompt` record containing both layers and approximate token estimates (GPT-style ~4 chars/token heuristic).

---

#### `service/LLMLinguaCompressor.java`
**LLMLingua-2 Single-Pass Token Compressor**.

In production, this calls a Python/FastAPI LLMLingua-2 sidecar. This implementation provides a deterministic rule-based approximation for testing:
1. Tokenises by whitespace.
2. Scores each token: stop-word penalty, length bonus, recency bias, capitalisation bonus, numeric bonus.
3. Protected phrases receive `Double.MAX_VALUE` score (never dropped).
4. Drops lowest-scoring tokens until `targetCompressionRatio` (default `0.5`) is met.

Configurable via `compression.target-ratio` in `application.properties`.

---

#### `service/ProtectionFloorEnforcer.java`
**Protection Floor Enforcer** — guarantees three phrase categories survive compression:

| Category | Pattern | Example |
|---|---|---|
| NER pseudonym placeholders | `{{TYPE_N}}` | `{{PERSON_1}}`, `{{EMAIL_1}}` |
| RAG-retrieved chunks | `[RAG: ...]` | `[RAG: Q3 revenue was $4.2M]` |
| Explicit protection markers | `[PROTECT: ...]` | `[PROTECT: critical clause]` |

Two-pass enforcement:
- **Pre-compression**: `extractProtectedPhrases(text)` — extracts all protected spans
- **Post-compression**: `enforceFloors(original, compressed)` — re-injects any dropped phrases

---

#### `service/ContextCompressionService.java`
**Pipeline Orchestrator** — wires all components into the full compression pipeline:

```
SanitizedContextDto
  → storeTurn (pgvector)
  → hybridRetrieve (Recency + MMR)
  → buildMessageList (history + current)
  → PromptStratifier.stratify()
  → ProtectionFloorEnforcer.extractProtectedPhrases()
  → LLMLinguaCompressor.compress()
  → ProtectionFloorEnforcer.enforceFloors()
  → CompressedPayloadDto
```

Uses module-local DTOs (`SanitizedContextDto`, `CompressedPayloadDto`) to avoid runtime coupling to the `contracts/` package. Includes a stub `generateEmbedding()` method (L2-normalised character-frequency vector) for testing without a sidecar.

---

#### `src/main/resources/application.properties`
Module configuration:
- Server port: `8082`
- PostgreSQL datasource pointing to `nexusgateway` database
- `compression.target-ratio=0.5`

---

### Unit Tests (`src/test/java/com/nexus/compression/`)

All tests are isolated — no Spring context is loaded, all external dependencies are mocked via Mockito.

| Test Class | Component Under Test | Key Scenarios |
|---|---|---|
| `VectorStoreRepositoryTest` | `VectorStoreRepository` | storeTurn persistence, recency window retrieval, MMR diversity selection, CSV float parsing |
| `PromptStratifierTest` | `PromptStratifier` | system→static, user/assistant→dynamic, tool→static, token estimation, empty input |
| `LLMLinguaCompressorTest` | `LLMLinguaCompressor` | token reduction at 0.5 ratio, protected phrase survival, empty input, stop-word priority, ratio=1.0 no-op |
| `ProtectionFloorEnforcerTest` | `ProtectionFloorEnforcer` | placeholder extraction, RAG marker extraction, re-injection of dropped phrases, no duplication, satisfiesFloors true/false |
| `ContextCompressionServiceTest` | `ContextCompressionService` | session/tenant propagation, NER placeholder survival end-to-end, system message stratification, retrieved chunks in output, hardRouteTier1 flag propagation |

---

## Modified Files (contracts/ — content only, no structural changes)

### `contracts/dto/SanitizedContext.java`
Filled in the empty contract DTO. Fields: `sessionId`, `tenantId`, `messages` (inner `Message` class with `role`/`content`), `pseudonymMapping`, `dataClassification`, `hardRouteTier1`, `securityMetadata`.

### `contracts/dto/CompressedPayload.java`
Filled in the empty contract DTO. Fields: `sessionId`, `tenantId`, `staticPrefix`, `compressedDynamicContext`, `protectedPhrases`, `originalTokenCount`, `compressedTokenCount`, `compressionRatio`, `retrievedChunks`, `hardRouteTier1`.

---

## Architecture Constraints Respected
- All new code is confined to `modules/context-compression/` (read/write allowed).
- `contracts/` DTOs were filled in (they were empty stubs) — no structural modifications to other modules.
- No files in `modules/ingress-shield/` or any other module were modified.
- All unit tests mock incoming/outgoing DTOs and do not wire the full application pipeline.
