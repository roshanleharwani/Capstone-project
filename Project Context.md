# Architecture

## Unified Architecture Overview

```
						  ┌───────────────────────────┐
                          │   Client / Application    │
                          │  (Tenant A, B, C, etc.)   │
                          └─────────────┬─────────────┘
                                        │
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ MODULE 1: INGRESS, PRIVACY, & AUTHORIZATION                                  │
│ • 3-Pass Detection: Regex ➔ Secret Scanner ➔ Transformer NER               │
│ • Masking: Session-stable placeholders + Do-Not-Mask allowlist               │
│ • Token Auth: Checks daily quota. If exhausted ➔ Trigger NexusMarket        │
└──────────────┬───────────────────────────────────────────────────────────────┘
               │
               ├─── Restricted Data ───► [ HARD-ROUTE TO TIER 1 ONLY ]
               │
               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ MODULE 2: NEXUSMARKET (Incentive & Quota Trading Engine)                     │
│ • Stackelberg Dynamic Pricing: Sets token exchange rates based on load       │
│ • VCG Auction: Allocates excess peak tokens to highest bidding tenants       │
│ • Immutable Ledger: Tracks borrowed tokens and priority credits              │
└──────────────┬───────────────────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ MODULE 3: CONTEXT MEMORY & COMPRESSION ENGINE                                │
│ • Semantic Shadow Cache: Recency Window + Top-K MMR Semantic Search          │
│ • Prompt Stratification: Static Prefix (Cached) vs Dynamic Payload           │
│ • Single-pass LLMLingua-2 Compressor (Protected Entity/Match Floor Ratios)   │
└──────────────┬───────────────────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ MODULE 4: DECOMPOSED ROUTER & CASCADE GUARD                                  │
│ • Fast-Path: Bypass pipeline for trivially short queries                     │
│ • Stage 1: Content-driven compression ratio prediction (ρ)                   │
│ • Stage 2: BEST-Route style router choosing (Tier, Model) based on ρ         │
└──────────────┬───────────────────────────────┬───────────────────────────────┘
               │                               │
               ▼                               ▼
┌──────────────────────────────┐┌──────────────────────────────────────────────┐
│ TIER 1: SELF-HOSTED (vLLM)   ││ TIER 2: PROPRIETARY APIs                     │
│ • White-Box Access           ││ • Black-Box Access (OpenAI, Anthropic, etc.) │
│ • PagedAttention             ││ • RAG over conversation history              │
│ • Semantic KV Offload/Onload ││ • Prompt-level compression                   │
└──────────────┬───────────────┘└──────────────┬───────────────────────────────┘
               │                               │
               └───────────────┬───────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ MODULE 5: CASCADE GUARD & EGRESS                                             │
│ • FrugalGPT Post-Hoc Reliability Score < Threshold?                          │
│   └── YES ──► Escalate (Reduce Compression / Larger Model / Retry)           │
│   └── NO  ──► De-pseudonymize & Deduct Tokens from Ledger & Return Response  │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Core Subsystems

### 1. Ingress & Privacy Shield (3-Pass + 3-Tier Hard Routing)

- **Detection Pipeline:**
    1. *Regex/Patterns:* Fast, zero-cost detection for SSNs, credit cards, emails, IP addresses.
    2. *Secret Scanners:* Entropy-based checks for API keys, bearer tokens, private credentials.
    3. *Transformer NER:* Context-aware detection for unstructured entities (names, organizations).
- **Data Classification & Enforcement:**
    - **Public:** Sent directly through the pipeline.
    - **Internal:** Masked using session-stable, type-preserving placeholders (`{{PERSON_1}}`). Respects a **Do-Not-Mask allowlist** (e.g., generic public geography, dates) to preserve task comprehension.
    - **Restricted:** **Hard-routed to Tier 1 (Self-Hosted)**. Restricted raw or masked data *never* leaves local infrastructure.
- **Egress Rehydration:** Server-side TTL mapping replaces placeholders back into raw values. Fallback to Cascade Guard if the LLM paraphrases or breaks placeholders.

### 2. Context Memory & Compression Allocator

- **Hybrid Retrieval (StreamingLLM + SnapKV principles):**
    - *Recency Window:* Unconditionally keeps the last $N$ turns for conversational coherence.
    - *Semantic Search:* Embeds the query and retrieves historical chunks using MMR (Maximal Marginal Relevance) to avoid redundant context.
    - *Sequence Order:* Retrieved turns are re-inserted in strict chronological order.
- **Compression & Protection Mechanics:**
    - *Prefix Stratification:* Static prefixes (System prompts, RAG schemas) are left uncompressed to maximize provider-side prompt caching.
    - *Anti-Compounding Floor:* Semantically retrieved chunks and NER-flagged entities are protected with a minimum compression floor ratio to prevent losing the exact context that triggered retrieval.
    - *Single-Pass Engine:* Uses single-pass compression (LLMLingua-2 style) to minimize critical-path latency stacking.

### 3. Decomposed Router & Cascade Guard

- **Two-Stage Routing:**
    - *Fast-Path Bypass:* Short/simple queries skip expensive pre-processing and hit a default cheap model directly.
    - *Stage 1:* Predicts a task-driven compression ratio $\rho$ using cheap surface features (query length, math/code markers, task type).
    - *Stage 2:* Feeds $\rho$ into a BEST-Route-style multi-head classifier to choose the optimal `(Tier, Model)` pairing against a reference model using a lightweight proxy reward model.
- **Cascade Guard (FrugalGPT style):**
    - Evaluates answer reliability via $g(q, a) \rightarrow [0, 1]$.
    - If below confidence threshold $\tau$, escalates: re-runs with lower compression or re-routes to a larger model before returning to the user.

### 4. Tier 1 Systems Payload (The Novel White-Box Component)

- **Target Environment:** Self-hosted open-weights models running on vLLM.
- **Semantic KV Cache Offload:**
    - Replaces permanent eviction (H2O/SnapKV drawback) with a dynamic **PagedAttention offload hook**.
    - Evicted blocks are swapped to host memory/NVMe instead of discarded.
    - When incoming query embeddings match offloaded block semantics, the blocks are rehydrated back into active GPU memory.

## Key Technical Constraints & Mitigations Acknowledged

1. **Calibration Dataset Priority:** Build a bespoke multi-task query evaluation set early to generate ground-truth labels for router training and capstone benchmarks.
2. **Latency Budgeting:** Keep the middleware overhead minimal; otherwise, latency stacking negates the cost/time benefits of model routing (triggering the fast-path).
3. **vLLM Integration Risk:** Treat the custom PagedAttention semantic offload hooks as high-risk engineering—ship Tier 2 baseline and routing core first, reserving Tier 1 offload as the primary stretch goal.

The biggest risk to any AI middleware gateway is latency. If you split the Ingress Privacy Shield, the Context Compressor, and the Router into entirely separate microservices, every single query will incur multiple network hops (via HTTP or gRPC) before it even reaches the LLM. Any time a distributed system scales upward, it raises the amount of data being transferred, which can introduce additional lag and slow down processing. This network overhead would completely negate the speed advantages of your Fast-Path bypass.

### The Solution: The Hybrid Architecture

**1. The Core Proxy (Modular Monolith)**

- **What it is:** The Ingress PII scanner, the Compression Engine, the Router logic, and the Cascade Guard should all be built as distinct, cleanly separated modules within a **single codebase and deployment unit**.
- **Why it works:** This keeps the prompt payload entirely in memory as it moves through the pipeline. Monoliths optimize for simplicity and development speed, while benefiting from simpler testing strategies and fast execution.
- **Tech Stack:** A single backend application acting as the proxy server.

**2. The Peripheral Ecosystem (Microservices)**
Everything outside of the critical, millisecond-sensitive pipeline should be decoupled into independent services. Because these run independently, you can deploy and scale each service as required.

- **Semantic Shadow Cache (Vector DB):** Your vector database (e.g., Milvus or pgvector) runs as its own containerized service.
- **Tier 1 LLM Engine:** The vLLM instance doing the KV offloading requires heavy GPU allocation, completely distinct from the CPU-heavy proxy. It must be a separate, isolated microservice.
- **Telemetry & Observability:** The Kafka message broker asynchronously ingesting your logs runs independently so it never blocks the proxy's main thread.
- **Control Plane Dashboard:** The Next.js frontend is deployed separately to handle user traffic, configurations, and visualization.

# **Multi-Tenant Token Sharing**

**The Problem:** In an enterprise or university setting with a multi-tenant LLM gateway, every tenant (e.g., CS Dept, Biotech Dept, Admin) is allocated a daily API token budget (e.g., 1,000,000 tokens/day).**The Inefficiency:** By 2:00 PM, the CS Dept runs out of tokens while running heavy agent workflows. Meanwhile, the Admin Dept has used only 50,000 tokens and will waste 950,000 tokens at midnight when quotas reset.**The Solution:** Allow idle tenants to "lend" or "sell" their unused token budget to budget-starved tenants in real time.  **The Incentive:** The lending department earns **Gateway Credits** or **Priority Queue Tokens**. Tomorrow, when the lending department has a heavy workload, they can redeem those credits to bypass rate limits or access higher-tier models.  

**Mechanism A: The Stackelberg Dynamic Pricing Engine**
Use a two-stage Stackelberg game (referenced in Section III.C of the paper):  
• **Stage 1 (Gateway Leader):** As global API token usage approaches daily caps, NexusGateway dynamically sets an internal exchange rate $p_t$ for borrowed tokens.
• **Stage 2 (Tenant Followers):** Users with low usage automatically opt into the pool to earn future credits, while high-usage tenants borrow from the pool.

**Mechanism B: VCG / Double Auction for Quota Allocation**
For peak hours when multiple tenants need extra tokens, implement a **Vickrey-Clarke-Groves (VCG) Sealed-Bid Auction** (referenced in Section IV.D of the paper):  
• Borrowing tenants submit secret bids (e.g., "I will trade 1.2 off-peak credits per 1,000 peak tokens needed").
• The auction allocates tokens to the highest bidder but charges them the price of the *second-highest* bid. This guarantees truthful bidding without market manipulation.  

**Mechanism C: The Multi-Tenant Credit Ledger**
Store user balances in a fast, auditable ledger (like Redis or PostgreSQL with cryptographic transaction hashing):  
• **Tenant A (Lender):** Account balance increases by $+X$ credits.
• **Tenant B (Borrower):** Account balance decreases by $-X$ credits, but daily token limit increases by $+N$ tokens.

## Deployed Architecture

```
								[ Internet ]
                                       │
                      [ NGINX Ingress Controller + ModSecurity WAF ]
                                       │
┌──────────────────────────────────────▼───────────────────────────────────────┐
│                    KUBERNETES CLUSTER (Rancher or Kubeadm)                   │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ CPU WORKER NODES                                                       │  │
│  │                                                                        │  │
│  │ [ Java Spring Boot Proxy Pods ]                                        │  │
│  │  1. Ingress & Transformer NER (Privacy Shield)                         │  │
│  │  2. NexusMarket: Stackelberg & VCG Auction Engine                      │  │
│  │  3. LLMLingua-2 Compression & Prompt Stratification                    │  │
│  │  4. Two-Stage Router & Cascade Guard                                   │  │
│  └────┬─────────────────────────────┬───────────────────────────────┬─────┘  │
│       │                             │                               │        │
│       ▼                             ▼                               ▼        │
│  ┌──────────────────┐      ┌─────────────────────────┐      ┌──────────────┐ │
│  │ TIER 1 INFERENCE │      │ STATE & TELEMETRY       │      │ TIER 2 APIs  │ │
│  │ GPU Worker Nodes │      │                         │      │              │ │
│  │                  │      │ • PostgreSQL + pgvector │      │ [NAT Router] │ │
│  │ [ vLLM Pods ]    │      │ • Valkey (Redis fork)   │      │      │       │ │
│  │                  │      │ • Apache Kafka (Strimzi)│      │  (OpenAI /   │ │
│  │                  │      │ • MinIO (Object Storage)│      │   Gemini)    │ │
│  └──────────────────┘      └─────────────────────────┘      └──────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 1. Task Division Matrix

```
                     ┌──────────────────────────────────────────┐
                     │          INCOMING CLIENT REQUEST         │
                     └────────────────────┬─────────────────────┘
                                          │
                                          ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ MEMBER 1: `ingress-shield/` (Zero-Trust Security & PII Lead)                    │
│ • Validates API Keys / JWTs & enforces mTLS boundaries                           │
│ • Runs 3-pass PII scanning (Regex, Secret, Transformer NER) & session masking   │
│ • Blocks Direct/Indirect Prompt Injections via Llama Guard / LLM Guard          │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ MEMBER 3: `market-incentives/` (Game Theory & Quota Lead)                       │
│ • Intercepts tenant request & checks daily API token quota balance              │
│ • Triggers Stackelberg dynamic pricing & VCG second-price token auctions        │
│ • Maintains immutable credit ledger & updates tenant account balances           │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ MEMBER 2: `context-compression/` (Context Memory & ML Compression Lead)         │
│ • Connects to pgvector Shadow Cache (Recency Window + Top-K MMR Retrieval)      │
│ • Separates Static Prefix (cached) from Dynamic Payload                         │
│ • Runs single-pass LLMLingua-2 compressor with protected entity floor ratios    │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ MEMBER 4: `router-dashboard/` (Systems Routing & Control Plane Lead)            │
│ • Stage 1: Predicts compression ratio (ρ) ➔ Stage 2: Selects Tier 1/2 Model   │
│ • Dispatches request to Tier 1 (vLLM PagedAttention) or Tier 2 (OpenAI/Gemini)   │
│ • Runs FrugalGPT Cascade Guard / Fast-Path & streams Kafka logs to Next.js UI   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Member 1: Security, Ingress & Zero-Trust Lead

- **Dedicated Module Directory:** `modules/ingress-shield/`
- **Core Responsibilities:**
    1. **OpenAI API Specification Endpoint:** Build the primary REST controller exposing `POST /v1/chat/completions`.
    2. **Zero-Trust Auth & Prompt Shields:** Validate incoming JWTs, API keys, and filter prompt injections.
    3. **3-Pass PII Engine:** Run Regex, Secret Scanner, and Transformer NER to swap PII out for session-stable placeholders (e.g., `{{PERSON_1}}`).
    4. **Egress De-pseudonymization:** Rehydrate placeholders back into original values on the return trip.
- **Independent Test Strategy:** Can mock the downstream response payload to test PII masking/unmasking in total isolation.

### Member 2: Context Memory & ML Compression Lead

- **Dedicated Module Directory:** `modules/context-compression/`
- **Core Responsibilities:**
    1. **Semantic Shadow Cache:** Integrate PostgreSQL `pgvector` to run hybrid retrieval (Recency Window + Top-K MMR) over chat history.
    2. **Prompt Stratifier:** Isolate Static Prefixes (system prompts, tool schemas) from Dynamic Payloads.
    3. **LLMLingua-2 Compression Engine:** Run single-pass token compression over dynamic context.
    4. **Protection Floor Enforcer:** Ensure NER-flagged entities and RAG-matched phrases are never compressed below minimum safety thresholds.
- **Independent Test Strategy:** Can feed uncompressed test prompts directly into their module and verify vector retrieval and compression ratios using standalone pytest/JUnit scripts.

### Member 3: Game Theory & Quota Incentive Lead

- **Dedicated Module Directory:** `modules/market-incentives/`
- **Core Responsibilities:**
    1. **Tenant Quota Tracking:** Intercept requests via `X-Tenant-ID` headers and verify available daily token balances.
    2. **Stackelberg Dynamic Pricing:** Calculate token borrowing rates based on system-wide peak loads.
    3. **VCG Second-Price Auction Engine:** Implement the auction solver to allocate pooled excess tokens to quota-starved tenants during peak hours.
    4. **Transactional Credit Ledger:** Manage the double-entry credit ledger stored in Valkey/PostgreSQL with Row-Level Security.
- **Independent Test Strategy:** Can simulate multi-tenant bidding requests using unit test scripts without running the full proxy pipeline or touching LLM models.

### Member 4: Two-Stage Router, Execution & Control Plane Lead

- **Dedicated Module Directory:** `modules/router-dashboard/`
- **Core Responsibilities:**
    1. **Two-Stage Decomposed Router:** Predict compression ratio $\rho$ (Stage 1) and route payload to optimal Tier 1 or Tier 2 model (Stage 2).
    2. **Tier 1 & Tier 2 Execution Connectors:** Maintain HTTP/gRPC clients for local vLLM instances and external APIs (OpenAI/Anthropic/Gemini).
    3. **Cascade Guard & Fast-Path:** Implement post-hoc reliability evaluation ($g(q,a)$) and fast-path bypass for short queries.
    4. **Kafka Telemetry & Control Plane UI:** Stream performance logs to Apache Kafka and build the Next.js developer dashboard to visualize cost savings, cache hits, and PII blocks.
- **Independent Test Strategy:** Can mock incoming compressed payloads and verify model routing logic, execution fallback, and dashboard metrics independently.

## 2. Monorepo Project Folder Structure

Plaintext

```
nexus-gateway/
├── README.md
├── docker-compose.yml              # Local dev environment (Postgres, Valkey, Kafka)
├── pom.xml / build.gradle          # Root build configuration
│
├── contracts/                      # SHARED CONTRACTS & DTOS (DO NOT MODIFY ALONE)
│   ├── dto/
│   │   ├── UserRequest.java        # Raw client payload
│   │   ├── SanitizedContext.java   # Output of Member 1
│   │   ├── QuotaApproval.java      # Output of Member 3
│   │   ├── CompressedPayload.java  # Output of Member 2
│   │   └── ModelResponse.java      # Output of Member 4
│   └── interfaces/
│       └── ModuleInterface.java    # Standard processing signatures
│
├── modules/                        # INDIVIDUAL DEDICATED FEATURE SUBFOLDERS
│   │
│   ├── ingress-shield/             # MEMBER 1 WORKSPACE
│   │   ├── src/main/java/com/nexus/ingress/
│   │   │   ├── controllers/        # OpenAI REST API Endpoint (/v1/chat/completions)
│   │   │   ├── auth/               # Dual JWT + API-Key Validators
│   │   │   ├── pii/                # 3-Pass PII Engine (Regex, Secret, Transformer NER)
│   │   │   ├── shields/            # Prompt Injection Filters (Llama Guard / LLM Guard)
│   │   │   └── masking/            # Session-Stable Mapping & Rehydration TTL Map
│   │   └── src/test/java/          # Member 1 Unit & Integration Tests
│   │
│   ├── context-compression/        # MEMBER 2 WORKSPACE
│   │   ├── src/main/java/com/nexus/compression/
│   │   │   ├── shadowcache/        # pgvector Semantic RAG & Recency Window Search
│   │   │   ├── stratifier/         # Static Prefix vs Dynamic Payload Splitter
│   │   │   ├── engine/             # LLMLingua-2 Single-Pass Token Compressor
│   │   │   └── protection/         # Entity & Keyword Floor Ratio Protection Rules
│   │   └── src/test/java/          # Member 2 Unit & Integration Tests
│   │
│   ├── market-incentives/          # MEMBER 3 WORKSPACE
│   │   ├── src/main/java/com/nexus/market/
│   │   │   ├── pricing/            # Stackelberg Dynamic Pricing Calculator
│   │   │   ├── auction/            # VCG Second-Price Auction Solver
│   │   │   ├── ledger/             # Immutable Double-Entry Transactional Ledger
│   │   │   └── quota/              # Multi-Tenant Token Quota Manager
│   │   └── src/test/java/          # Member 3 Unit & Integration Tests
│   │
│   └── router-dashboard/           # MEMBER 4 WORKSPACE
│       ├── src/main/java/com/nexus/router/
│       │   ├── stage1/             # Content Compression Ratio Predictor (ρ)
│       │   ├── stage2/             # BEST-Route Style (Tier, Model) Classifier
│       │   ├── connectors/         # Tier 1 (vLLM) & Tier 2 (OpenAI/Gemini) HTTP Clients
│       │   ├── guard/              # Cascade Guard & Fast-Path Bypass Logic
│       │   └── telemetry/          # Kafka Event Publisher
│       └── src/test/java/          # Member 4 Unit & Integration Tests
│
├── dashboard/                      # FRONTEND CONTROL PLANE (MEMBER 4 CO-OWNED)
│   ├── package.json
│   ├── src/
│   │   ├── components/             # Cost, PII Interceptions, & Token Analytics Charts
│   │   └── pages/                  # Next.js Developer Dashboard App
│
└── deploy/                         # INFRASTRUCTURE DEPLOYMENT MANIFESTS
    ├── helm/                       # Kubernetes Helm Charts (Open-Source Deployment)
    ├── kube/                       # Istio Service Mesh & Network Policies
    └── configs/                    # PostgreSQL (pgvector), Valkey, & Strimzi Kafka Configs
```