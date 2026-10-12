# NexusGateway — Project Guide for Cursor & New Collaborators

> **For:** Anyone picking up this project fresh.
> **Assumes:** AWS CLI installed and configured with the project AWS account (`611597330624`, region `ap-south-1`/Mumbai), Docker Desktop running, `kubectl` and PowerShell available.

---

## Table of Contents

1. [What Is NexusGateway?](#1-what-is-nexusgateway)
2. [AWS Architecture](#2-aws-architecture)
3. [Module Breakdown](#3-module-breakdown)
4. [Cluster Lifecycle — Start / Stop / Destroy](#4-cluster-lifecycle)
5. [First-Time Full Deploy](#5-first-time-full-deploy)
6. [Deploying Code Changes](#6-deploying-code-changes)
7. [Local Development](#7-local-development)
8. [Key Config & Secrets](#8-key-config--secrets)
9. [Directory Structure](#9-directory-structure)
10. [Common kubectl Commands](#10-common-kubectl-commands)
11. [Gotchas & Known Issues](#11-gotchas--known-issues)

---

## 1. What Is NexusGateway?

NexusGateway is an **enterprise-grade AI proxy/gateway** that sits between client applications and LLM backends. It provides:

- **PII Detection & Masking** — scans requests for Personally Identifiable Information before they reach the LLM
- **Prompt Injection Shielding** — blocks adversarial prompts
- **Tiered Inference Routing** — routes requests to the cheapest/fastest backend (AWS Bedrock → OpenAI → self-hosted LLaMA)
- **Market Incentive Engine** — VCG auction-based quota allocation across tenants
- **Context Compression** — reduces token usage via pgvector + LLMLingua-2
- **Telemetry** — publishes events to Kafka for downstream analytics
- **Next.js Dashboard** — real-time monitoring UI

The system exposes an **OpenAI-compatible API** (`POST /v1/chat/completions`) so any OpenAI SDK works without changes.

---

## 2. AWS Architecture

```
Internet / Client Apps
        │
        ▼
┌───────────────────────────────────────────────────────────┐
│              EKS Cluster: nexus-gateway-cluster           │
│              Region: ap-south-1 (Mumbai)                  │
│              Node Group: cpu-node-group (t3.large × 2-5)  │
│              Namespace: nexus-gateway                      │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  nexus-proxy  (3 replicas, port 8080)               │  │
│  │  Main Spring Boot gateway monolith                  │  │
│  │  Image: ECR → nexus-gateway-proxy:latest            │  │
│  │                                                     │  │
│  │  Internally calls:                                  │  │
│  │    ├── ingress-shield  (PII, Auth, masking)         │  │
│  │    ├── router-dashboard (LLM routing)               │  │
│  │    ├── market-incentives (quota/billing)            │  │
│  │    └── context-compression (token reduction)       │  │
│  └──────────────────────┬──────────────────────────────┘  │
│                         │                                 │
│          ┌──────────────┼──────────────┐                  │
│          ▼              ▼              ▼                  │
│   ┌────────────┐ ┌────────────┐ ┌──────────────────┐     │
│   │ PostgreSQL │ │   Valkey   │ │  Kafka Cluster   │     │
│   │ (Bitnami   │ │ (Redis-    │ │  nexus-kafka     │     │
│   │  Helm)     │ │  compat,   │ │  port 9092       │     │
│   │ port 5432  │ │  Bitnami)  │ │                  │     │
│   │ db:        │ │ standalone │ └──────────────────┘     │
│   │ nexus_gw   │ └────────────┘                          │
│   └────────────┘                                         │
│                                                           │
│  ┌──────────────────────────────────────────┐            │
│  │ router-dashboard  (2 replicas, port 8084)│            │
│  │ LLM routing logic                        │            │
│  │ Tier 1 → AWS Bedrock (Llama3-8B)         │            │
│  │ Tier 2 → OpenAI GPT-4                    │            │
│  │ Tier 3 → nexus-vllm (self-hosted llama)  │            │
│  └──────────────────────────────────────────┘            │
│                                                           │
│  ┌──────────────────────────────────────────┐            │
│  │ nexus-vllm / nexus-llamacpp (port 8000)  │            │
│  │ Self-hosted LLaMA 3 8B via llama.cpp     │            │
│  │ Downloads model from HuggingFace on boot │            │
│  └──────────────────────────────────────────┘            │
│                                                           │
│  ┌──────────────────────────────────────────┐            │
│  │ nexus-dashboard  (Next.js, port 3000)    │            │
│  │ Image: ECR → nexus-gateway-dashboard     │            │
│  └──────────────────────────────────────────┘            │
└───────────────────────────────────────────────────────────┘

ECR Repositories (AWS Account 611597330624, ap-south-1):
  • nexus-gateway-proxy      ← main Spring Boot monolith
  • nexus-gateway-dashboard  ← Next.js UI
  • nexus-gateway-router     ← router-dashboard Spring Boot service

Terraform manages: VPC, subnets, EKS cluster, node group, ECR repos
Helm manages: PostgreSQL, Valkey (Redis)
kubectl manages: all application deployments & services
```

### AWS Services Used

| Service | Purpose | Cost Notes |
|---------|---------|-----------|
| **EKS** | Kubernetes control plane | $0.10/hr always-on (stop nodes to save EC2 cost) |
| **EC2 (t3.large)** | Worker nodes (2–5) | ~$0.08/hr per node — scale to 0 to stop billing |
| **ECR** | Docker image registry | ~free for small images |
| **AWS Bedrock** | Tier-1 LLM inference (Llama3-8B) | Pay-per-token, uses node IAM role (no API key) |
| **VPC** | Network isolation | ~free |

---

## 3. Module Breakdown

All modules live in `nexus-gateway/modules/`. Each is an independent Spring Boot service built with Maven.

### `ingress-shield` — Port 8080 (embedded in proxy)
The security and PII layer. Every request passes through this first.

| Class | What it does |
|-------|-------------|
| `DualLayerAuthFilter` | JWT + mTLS authentication |
| `TransformerNERScanner` | NER-based PII detection (calls Python NER service or regex fallback) |
| `SecretScanner` | Regex-based secrets detection (API keys, passwords in prompt) |
| `PseudonymizationEngine` | Replaces PII with reversible pseudonyms before sending to LLM |
| `OpenAIChatController` | Main `/v1/chat/completions` endpoint |
| `SecurityConfig` | Spring Security config |

### `router-dashboard` — Port 8084
The intelligent LLM routing layer with 3-tier fallback.

| Class | What it does |
|-------|-------------|
| `RouterController` | REST endpoint for routing decisions |
| `CascadeGuard` | Orchestrates tier fallback: Bedrock → OpenAI → local llama |
| `Tier1Connector` | AWS Bedrock (Llama3-8B) — uses IAM instance profile, no key needed |
| `Tier2Connector` | OpenAI GPT-4 — uses API key from `application.yml` |
| `VllmConnector` | Self-hosted llama.cpp at `nexus-vllm:8000` |
| `CompressionPredictor` | Decides if context compression should run (Stage 1) |
| `KafkaTelemetryPublisher` | Publishes routing events to Kafka |

### `market-incentives` — Embedded in proxy
Tenant quota management using game-theory pricing.

| Class | What it does |
|-------|-------------|
| `MarketIncentiveService` | Orchestrates quota approval flow |
| `TenantQuotaManager` | Tracks per-tenant token usage |
| `VcgAuctionSolver` | VCG mechanism for fair resource allocation |
| `StackelbergPricingEngine` | Dynamic pricing based on demand |
| `CreditLedgerService` | Persists billing records to PostgreSQL |

### `context-compression` — Embedded in proxy
Reduces token count of long conversations before sending to LLM.

| Class | What it does |
|-------|-------------|
| `ContextCompressionService` | Main orchestrator |
| `PromptStratifier` | Splits prompt into system/user/history layers |
| `LLMLinguaCompressor` | Calls LLMLingua-2 (Python) for compression |
| `ProtectionFloorEnforcer` | Ensures compression doesn't drop critical content |

### `nexus-gateway-app` — The monolith aggregator
Ties all modules together into a single deployable Spring Boot app.

| Class | What it does |
|-------|-------------|
| `DashboardController` | Serves threat logs to the Next.js dashboard |
| `SettingsController` | Proxy configuration API |
| `ThreatLog` / `ThreatLogRepository` | JPA entity for storing detected threats in PostgreSQL |

### `dashboard/` — Next.js UI (Port 3000)
Located at `nexus-gateway/dashboard/`. Talks to the proxy via internal API routes.

| Route | What it shows |
|-------|--------------|
| `/` | Main dashboard — live request stats |
| `/pii` | PII detection events |
| `/quota` | Per-tenant quota usage |
| `/settings` | Gateway configuration |
| `/cache` | Context compression cache hits |

---

## 4. Cluster Lifecycle

> All scripts are in `nexus-gateway/`. Run them from that directory.

### ▶ Start the Cluster

```powershell
cd nexus-gateway
powershell -ExecutionPolicy Bypass -File cluster-start.ps1
```

**What it does:**
- If cluster doesn't exist → runs `terraform apply` (takes **15–20 min** first time)
- If cluster exists but nodes = 0 → scales node group back to 3 nodes
- Updates your local `~/.kube/config` automatically
- Attaches `AmazonEC2ContainerRegistryReadOnly` IAM policy to nodes
- Waits for nodes to be `Ready`
- Port-forwards `nexus-proxy` to `localhost:8081`

### ⏹ Stop the Cluster (Save EC2 Cost)

```powershell
powershell -ExecutionPolicy Bypass -File cluster-stop.ps1
```

**What it does:**
- Scales the node group to **0 nodes** — all EC2 instances terminate
- EKS control plane stays running (costs $0.10/hr still)
- All Kubernetes config (deployments, services, configmaps) is **preserved**
- Restart anytime with `cluster-start.ps1`
- Takes ~2–3 minutes for nodes to fully terminate

> ✅ Use this for overnight/weekend cost savings. The cluster "remembers" all deployments.

### 💣 Destroy the Cluster (Zero Cost)

```powershell
powershell -ExecutionPolicy Bypass -File cluster-destroy.ps1
```

**What it does:**
- Permanently deletes the EKS node group and the EKS cluster
- Eliminates the $0.10/hr control plane charge too
- **Next start will run full `terraform apply`** — takes 15–20 min to rebuild

> ⚠️ Use this only for long breaks (weeks). Everything is recreated from Terraform, no data is lost from ECR.

### 🗑 Full Terraform Destroy (Nuclear Option)

```powershell
cd nexus-gateway/deploy/terraform
terraform destroy -auto-approve
```

Destroys **everything** including VPC, ECR repos. You'll need to re-push Docker images too.

---

## 5. First-Time Full Deploy

After `terraform apply` completes (via `cluster-start.ps1`), deploy the application stack:

### Step 1 — Create Namespace

```bash
kubectl create namespace nexus-gateway
```

### Step 2 — Install Stateful Services (PostgreSQL + Valkey)

```powershell
cd nexus-gateway
powershell -ExecutionPolicy Bypass -File deploy\helm\install-state.ps1
```

This installs via Bitnami Helm charts:
- **PostgreSQL** — db: `nexus_gateway`, password: `nexus_secret`
- **Valkey (Redis-compatible)** — password: `valkey_secret`

### Step 3 — Install Kafka

```bash
kubectl apply -f deploy/configs/kafka-cluster.yaml -n nexus-gateway
```

Wait ~2 min for Kafka to be ready:

```bash
kubectl get pods -n nexus-gateway | grep kafka
```

### Step 4 — Build & Push Docker Images

#### Main Proxy (nexus-gateway-app):
```powershell
cd nexus-gateway
$ACCOUNT = "611597330624"
$REGION  = "ap-south-1"
# Login to ECR
cmd.exe /c "aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"

# Build and push
docker build -t ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/nexus-gateway-proxy:latest .
docker push ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/nexus-gateway-proxy:latest
```

#### Router Dashboard:
```powershell
powershell -ExecutionPolicy Bypass -File deploy-router.ps1
```

### Step 5 — Apply Kubernetes Manifests

```bash
kubectl apply -f deploy/kube/ -n nexus-gateway
```

### Step 6 — Verify Everything is Running

```bash
kubectl get pods -n nexus-gateway
kubectl get services -n nexus-gateway
```

Expected pods:
```
nexus-proxy-xxx          Running  (3 replicas)
router-dashboard-xxx     Running  (2 replicas)
postgres-postgresql-xxx  Running
valkey-redis-master-xxx  Running
nexus-kafka-xxx          Running
```

### Step 7 — Access the Gateway

```bash
# In a separate terminal — keep this running
kubectl port-forward svc/nexus-proxy -n nexus-gateway 8081:8080
```

Test it:
```bash
curl -X POST http://localhost:8081/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-token" \
  -d '{"model":"llama3","messages":[{"role":"user","content":"Hello!"}]}'
```

---

## 6. Deploying Code Changes

### Changed `router-dashboard` module?

```powershell
cd nexus-gateway
# Rebuild JAR
cd modules/router-dashboard && mvn package -DskipTests && cd ../..

# Build, push, redeploy
powershell -ExecutionPolicy Bypass -File deploy-router.ps1
```

### Changed `nexus-gateway-app` (the main proxy)?

```powershell
cd nexus-gateway
mvn package -DskipTests -pl nexus-gateway-app -am

$ACCOUNT = "611597330624"; $REGION = "ap-south-1"
cmd.exe /c "aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
docker build -t ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/nexus-gateway-proxy:latest .
docker push ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/nexus-gateway-proxy:latest
kubectl rollout restart deployment/nexus-proxy -n nexus-gateway
```

### Changed the Next.js Dashboard?

```powershell
cd nexus-gateway/dashboard
$ACCOUNT = "611597330624"; $REGION = "ap-south-1"
cmd.exe /c "aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
docker build -t ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/nexus-gateway-dashboard:latest .
docker push ${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com/nexus-gateway-dashboard:latest
kubectl rollout restart deployment/nexus-dashboard -n nexus-gateway
```

---

## 7. Local Development

You don't need the EKS cluster running for local dev. Run services locally:

### Run the main proxy locally:

```bash
cd nexus-gateway/nexus-gateway-app
mvn spring-boot:run
# Runs on http://localhost:8080
```

### Run the router locally:

```bash
cd nexus-gateway/modules/router-dashboard
mvn spring-boot:run
# Runs on http://localhost:8084
```

### Run the Next.js dashboard locally:

```bash
cd nexus-gateway/dashboard
npm install
npm run dev
# Runs on http://localhost:3000
```

> For local dev the dashboard talks to the proxy at `http://localhost:8080` via the internal API routes in `src/app/internal/`.

### Run tests:

```bash
cd nexus-gateway/modules/router-dashboard
mvn test

cd nexus-gateway/modules/ingress-shield
mvn test

cd nexus-gateway/modules/market-incentives
mvn test
```

---

## 8. Key Config & Secrets

### LLM Backends

| Tier | Provider | Config Location | Auth |
|------|---------|----------------|------|
| Tier 1 | AWS Bedrock (Llama3-8B) | `application.yml` → `nexus.bedrock.*` | IAM Instance Profile (auto) |
| Tier 2 | OpenAI GPT-4 | `application.yml` → `openai.api.key` | API key hardcoded |
| Tier 3 | Self-hosted llama.cpp | `nexus-vllm:8000` (in-cluster) | None |

**Bedrock model ID:** `meta.llama3-8b-instruct-v1:0`  
**Bedrock region:** `ap-south-1`

> The Bedrock tier uses the EKS node's **IAM instance profile** — no API key needed. If running locally, you need AWS credentials configured (`aws configure`).

### Database

| Service | Host (in-cluster) | Port | DB | User | Password |
|---------|------------------|------|-----|------|---------|
| PostgreSQL | `postgres-postgresql.nexus-gateway.svc.cluster.local` | 5432 | `nexus_gateway` | `postgres` | `nexus_secret` |
| Valkey/Redis | `valkey-redis-master.nexus-gateway.svc.cluster.local` | 6379 | — | — | `valkey_secret` |
| Kafka | `nexus-kafka-kafka-bootstrap.nexus-gateway.svc.cluster.local` | 9092 | — | — | — |

### AWS Account Info

```
Account ID : 611597330624
Region     : ap-south-1 (Mumbai)
Cluster    : nexus-gateway-cluster
Namespace  : nexus-gateway
```

### ECR Image URLs

```
nexus-gateway-proxy:     611597330624.dkr.ecr.ap-south-1.amazonaws.com/nexus-gateway-proxy:latest
nexus-gateway-dashboard: 611597330624.dkr.ecr.ap-south-1.amazonaws.com/nexus-gateway-dashboard:latest
nexus-gateway-router:    611597330624.dkr.ecr.ap-south-1.amazonaws.com/nexus-gateway-router:latest
```

---

## 9. Directory Structure

```
Captsone/
├── nexus-gateway/
│   ├── cluster-start.ps1          ← Start EKS cluster
│   ├── cluster-stop.ps1           ← Scale nodes to 0 (save cost)
│   ├── cluster-destroy.ps1        ← Full EKS deletion (max savings)
│   ├── deploy-router.ps1          ← Build+push router-dashboard image
│   │
│   ├── dashboard/                 ← Next.js monitoring UI (port 3000)
│   │   └── src/app/
│   │       ├── page.tsx           ← Main dashboard
│   │       ├── settings/          ← Settings page
│   │       ├── pii/               ← PII events
│   │       ├── quota/             ← Tenant quota usage
│   │       └── internal/          ← API proxy routes (gateway/, settings/)
│   │
│   ├── deploy/
│   │   ├── terraform/             ← IaC: VPC, EKS, ECR (main.tf, eks.tf, etc.)
│   │   ├── helm/
│   │   │   └── install-state.ps1  ← Deploy PostgreSQL + Valkey via Helm
│   │   ├── configs/
│   │   │   └── kafka-cluster.yaml ← Kafka cluster manifest
│   │   └── kube/                  ← All K8s Deployment + Service manifests
│   │       ├── nexus-proxy.yaml   ← Main proxy (3 replicas)
│   │       ├── router-dashboard.yaml
│   │       ├── nexus-vllm.yaml    ← Self-hosted llama.cpp
│   │       ├── nexus-dashboard.yaml
│   │       └── nexus-ingress.yaml
│   │
│   ├── modules/
│   │   ├── ingress-shield/        ← PII scanning, auth, masking (port 8080)
│   │   ├── router-dashboard/      ← LLM routing: Bedrock→OpenAI→local (port 8084)
│   │   ├── market-incentives/     ← Quota, billing, VCG auction
│   │   └── context-compression/  ← Token reduction via LLMLingua-2
│   │
│   ├── nexus-gateway-app/         ← Main Spring Boot monolith (aggregates all modules)
│   │
│   ├── test.ps1                   ← Quick API smoke test
│   ├── test-pii-more.ps1          ← PII detection test suite
│   ├── test-quota.ps1             ← Quota enforcement tests
│   └── test-router.ps1            ← Routing tier tests
│
├── conferencePaperTemplate.tex    ← Academic paper
└── .gitignore                     ← Excludes target/, *.class, *.jar, tfstate
```

---

## 10. Common kubectl Commands

```bash
# See all running pods
kubectl get pods -n nexus-gateway

# Tail logs from proxy
kubectl logs -f deployment/nexus-proxy -n nexus-gateway

# Tail logs from router
kubectl logs -f deployment/router-dashboard -n nexus-gateway

# Restart a deployment after image push
kubectl rollout restart deployment/nexus-proxy -n nexus-gateway

# Port-forward proxy to localhost:8081
kubectl port-forward svc/nexus-proxy -n nexus-gateway 8081:8080

# Port-forward dashboard to localhost:3000
kubectl port-forward svc/nexus-dashboard -n nexus-gateway 3000:3000

# Describe a pod (see error events)
kubectl describe pod <pod-name> -n nexus-gateway

# Shell into a running pod
kubectl exec -it deployment/nexus-proxy -n nexus-gateway -- /bin/sh

# Check node status
kubectl get nodes

# Scale proxy manually
kubectl scale deployment nexus-proxy --replicas=2 -n nexus-gateway
```

---

## 11. Gotchas & Known Issues

### 1. `cluster-start.ps1` first run takes 15–20 minutes
Terraform provisions the entire VPC + EKS cluster. Totally normal. Don't interrupt it.

### 2. Bedrock Tier-1 only works in-cluster or with AWS credentials
`Tier1Connector` uses the EC2 instance profile IAM role. When running **locally**, ensure `aws configure` is set up and your IAM user has `bedrock:InvokeModel` permission.

### 3. Don't commit `target/` directories or `terraform.tfstate`
Both are in `.gitignore`. The `target/` dirs contain 50MB+ JARs that will break git push. Never run `git add -A` without checking first — always review `git status`.

### 4. OpenAI API key is hardcoded in `application.yml`
Found in `modules/router-dashboard/src/main/resources/application.yml`. If it expires, replace it there and redeploy the router.

### 5. Tier-3 GPU LLM (vLLM on `g4dn.xlarge`) stays at desired_size=0 by default
AWS G/VT quota in `ap-south-1` allows up to 4 instances. Terraform defines `gpu_llm_nodes` but **does not start GPUs** until you scale up:

```powershell
powershell -ExecutionPolicy Bypass -File deploy\scripts\llm-gpu-scale.ps1 -Action up
kubectl apply -f deploy/kube/nvidia-device-plugin.yaml
kubectl apply -f deploy/kube/nexus-vllm.yaml -n nexus-gateway
```

Scale back to 0 with `-Action down` to stop GPU billing. HF gated models need `HF_TOKEN` in secret `nexus-vllm-hf`.

### 6. Kafka must be running before nexus-proxy starts
If `KafkaTelemetryPublisher` can't connect, the proxy will still serve requests but telemetry events will be lost. Deploy kafka first (step 3 of first-time deploy).

### 7. `cluster-stop.ps1` preserves all K8s state
When you scale nodes back up with `cluster-start.ps1`, all deployments auto-reschedule. No need to `kubectl apply` again unless you changed manifests.

### 8. GitHub repo was renamed
The remote was renamed from `Captsone` → `Capstone-project`. Use:
```bash
git remote set-url origin https://github.com/roshanleharwani/Capstone-project.git
```

---

*Last updated: October 2026*
