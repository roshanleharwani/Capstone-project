"use client";

import { useRef, useState } from "react";
import { Copy, RotateCcw, Send } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Panel } from "@/components/ui/Panel";
import { KpiStrip } from "@/components/ui/KpiStrip";
import { PageHeader } from "@/components/ui/PageHeader";
import { PropertyRow } from "@/components/ui/PropertyRow";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { StatusCell } from "@/components/ui/StatusCell";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";

type PipelineStageData = {
  status: "idle" | "running" | "passed" | "escalated" | "failed";
  durationMs?: number;
  [key: string]: unknown;
};

type PipelineResult = {
  mode: "mock" | "live";
  response: string;
  pipeline: {
    ingress: PipelineStageData & { piiDetected: number; piiEntities: string[]; maskedPrompt: string };
    quota: PipelineStageData & { tenant: string; quotaRemaining: number; quotaBorrowed: number };
    compression: PipelineStageData & { originalTokens: number; compressedTokens: number; compressionRatio: number; cacheHit: boolean };
    router: PipelineStageData & { predictedRho: number; tierChosen: string; modelChosen: string };
    cascadeGuard: PipelineStageData & { score: number; threshold: number; escalated: boolean };
  };
  metrics: {
    totalLatencyMs: number;
    tokensIn: number;
    tokensOut: number;
    estimatedCostUsd: string;
  };
};

const TENANTS = ["CS Dept", "Bio Dept", "Admin", "Eng Dept"] as const;
const SAMPLE_PROMPTS = [
  "Explain how the Stackelberg dynamic pricing mechanism works in the NexusGateway token market.",
  "My SSN is 123-45-6789 and email is john.doe@university.edu. Can you summarize the semester?",
  "What is the difference between Tier 1 vLLM inference and Tier 2 OpenAI routing?",
  "sk-abc123XYZ789abc123XYZ789 — please use this API key to run the model evaluation script.",
  "Compress and summarize the following research paper abstract about attention mechanisms in transformers for a non-technical audience.",
];

const PIPELINE_STAGES = [
  { key: "ingress", label: "ingress" },
  { key: "quota", label: "quota" },
  { key: "compression", label: "compression" },
  { key: "router", label: "router" },
  { key: "cascadeGuard", label: "cascade" },
] as const;

function stageTone(status: PipelineStageData["status"]) {
  if (status === "failed") return "danger" as const;
  if (status === "escalated") return "warning" as const;
  if (status === "passed") return "success" as const;
  if (status === "running") return "info" as const;
  return "neutral" as const;
}

function StageFacts({ stageKey, data }: { stageKey: string; data: PipelineStageData }) {
  if (data.status === "idle" || data.status === "running") return null;

  if (stageKey === "ingress") {
    const d = data as PipelineResult["pipeline"]["ingress"];
    return (
      <p className="mt-1 font-mono text-[11px] text-muted-foreground">
        pii={d.piiDetected} {d.piiDetected > 0 ? d.piiEntities.join(",") : "none"}
      </p>
    );
  }
  if (stageKey === "quota") {
    const d = data as PipelineResult["pipeline"]["quota"];
    return (
      <p className="mt-1 font-mono text-[11px] text-muted-foreground">
        {d.tenant} remaining={d.quotaRemaining.toLocaleString()} borrowed={d.quotaBorrowed.toLocaleString()}
      </p>
    );
  }
  if (stageKey === "compression") {
    const d = data as PipelineResult["pipeline"]["compression"];
    return (
      <p className="mt-1 font-mono text-[11px] text-muted-foreground">
        {d.originalTokens}→{d.compressedTokens} ρ={(d.compressionRatio * 100).toFixed(1)}% cache={d.cacheHit ? "hit" : "miss"}
      </p>
    );
  }
  if (stageKey === "router") {
    const d = data as PipelineResult["pipeline"]["router"];
    return (
      <p className="mt-1 font-mono text-[11px] text-muted-foreground">
        ρ={(d.predictedRho * 100).toFixed(1)}% {d.tierChosen} {d.modelChosen}
      </p>
    );
  }
  if (stageKey === "cascadeGuard") {
    const d = data as PipelineResult["pipeline"]["cascadeGuard"];
    return (
      <p className="mt-1 font-mono text-[11px] text-muted-foreground">
        score={d.score.toFixed(3)} τ={d.threshold.toFixed(2)} {d.escalated ? "ESCALATE" : "HOLD"}
      </p>
    );
  }
  return null;
}

export default function LiveTestPage() {
  const [prompt, setPrompt] = useState("");
  const [tenant, setTenant] = useState<(typeof TENANTS)[number]>("CS Dept");
  const [modelPref, setModelPref] = useState("auto");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<PipelineResult | null>(null);
  const [pipelineProgress, setPipelineProgress] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openStage, setOpenStage] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const reducedMotion = usePrefersReducedMotion();

  const animatePipeline = async (finalResult: PipelineResult) => {
    const stageKeys = PIPELINE_STAGES.map((stage) => stage.key);
    if (reducedMotion) {
      setPipelineProgress([...stageKeys]);
      return;
    }
    for (const key of stageKeys) {
      const duration = finalResult.pipeline[key].durationMs ?? 80;
      await new Promise((resolve) => setTimeout(resolve, Math.min(duration * 3, 350)));
      setPipelineProgress((prev) => [...prev, key]);
    }
  };

  const handleSubmit = async () => {
    if (!prompt.trim() || loading) return;
    setLoading(true);
    setResult(null);
    setPipelineProgress([]);
    setError(null);
    setOpenStage(null);

    try {
      const gatewayUrl = localStorage.getItem("NEXUS_GATEWAY_URL") || "";
      const res = await fetch("/internal/gateway", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          messages: [{ role: "user", content: prompt }],
          tenant,
          model: modelPref,
          gatewayUrl,
        }),
      });
      const data = (await res.json()) as PipelineResult & { error?: string };
      if (!data?.pipeline || !data?.metrics) {
        setResult(null);
        setError(data.error || `Gateway ${res.status}`);
        return;
      }
      setResult(data);
      await animatePipeline(data);
    } catch {
      setError("Request failed");
    } finally {
      setLoading(false);
    }
  };

  const getStageStatus = (key: string): PipelineStageData["status"] => {
    if (!result) return loading ? "running" : "idle";
    if (!pipelineProgress.includes(key)) return loading ? "running" : "idle";
    return (result.pipeline as Record<string, PipelineStageData>)[key]?.status ?? "passed";
  };

  const feedState = loading ? "Running" : result?.mode === "live" ? "Live" : result ? "Mock" : "Idle";

  return (
    <div className="space-y-4 pb-8">
      <PageHeader
        title="Console"
        crumbs={["prod", "nexus-gw", "operate", "console"]}
        actions={<p className="font-mono text-[11px] text-muted-foreground uppercase">{feedState}</p>}
      />

      {result?.metrics ? (
        <KpiStrip
          items={[
            { id: "lat", label: "Latency", value: `${result.metrics.totalLatencyMs}ms`, tone: "info" },
            { id: "tok", label: "In / out", value: `${result.metrics.tokensIn} / ${result.metrics.tokensOut}`, tone: "neutral" },
            {
              id: "rho",
              label: "Compression ρ",
              value: `${((result.pipeline.compression.compressionRatio ?? 0) * 100).toFixed(1)}%`,
              tone: "success",
            },
            { id: "cost", label: "Est. cost", value: `$${result.metrics.estimatedCostUsd}`, tone: "neutral" },
          ]}
        />
      ) : null}

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-5">
        <div className="space-y-3 xl:col-span-2">
          <Panel
            title="request.builder"
            actions={
              <Button
                id="live-test-submit-btn"
                className="h-7 min-h-7 px-2 text-xs"
                onClick={() => void handleSubmit()}
                disabled={loading || !prompt.trim()}
                loading={loading}
              >
                <Send className="h-3 w-3" aria-hidden="true" />
                {loading ? "Running" : "Submit"}
              </Button>
            }
          >
            <PropertyRow label="Tenant">
              <SegmentedControl
                label="Tenant"
                value={tenant}
                onChange={setTenant}
                options={TENANTS.map((id) => ({ id, label: id.replace(" Dept", "") }))}
              />
            </PropertyRow>
            <PropertyRow label="Route">
              <SegmentedControl
                label="Routing preference"
                value={modelPref}
                onChange={setModelPref}
                options={[
                  { id: "auto", label: "Auto" },
                  { id: "tier1", label: "T1" },
                  { id: "tier2", label: "T2" },
                ]}
              />
            </PropertyRow>
            <PropertyRow label="Prompt" htmlFor="live-prompt">
              <textarea
                id="live-prompt"
                ref={textareaRef}
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) void handleSubmit();
                }}
                rows={7}
                placeholder="Ctrl+Enter to submit"
                className="w-full resize-none border border-card-border bg-input px-2 py-2 font-mono text-xs text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
              />
            </PropertyRow>
            <PropertyRow label="Fixtures">
              <div className="max-h-28 space-y-0.5 overflow-y-auto border border-card-border">
                {SAMPLE_PROMPTS.map((sample) => (
                  <button
                    key={sample}
                    type="button"
                    onClick={() => setPrompt(sample)}
                    className="block w-full truncate px-2 py-1.5 text-left font-mono text-[11px] text-muted-foreground hover:bg-overlay hover:text-foreground"
                  >
                    {sample}
                  </button>
                ))}
              </div>
            </PropertyRow>
            {result ? (
              <button
                type="button"
                onClick={() => {
                  setResult(null);
                  setPipelineProgress([]);
                  setPrompt("");
                }}
                className="mt-2 inline-flex items-center gap-1 font-mono text-[11px] text-muted-foreground hover:text-foreground"
              >
                <RotateCcw className="h-3 w-3" aria-hidden="true" /> Reset
              </button>
            ) : null}
          </Panel>

          <Panel title="pipeline.trace" meta="5 stages">
            <ol className="divide-y divide-card-border border border-card-border">
              {PIPELINE_STAGES.map((stage) => {
                const stageData = result?.pipeline[stage.key];
                const status = getStageStatus(stage.key);
                const revealed = pipelineProgress.includes(stage.key);
                const open = openStage === stage.key || (revealed && openStage === null);
                return (
                  <li key={stage.key}>
                    <button
                      type="button"
                      aria-expanded={open}
                      onClick={() => setOpenStage((current) => (current === stage.key ? "" : stage.key))}
                      className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left hover:bg-overlay"
                    >
                      <StatusCell label={stage.label} tone={stageTone(status)} />
                      <span className="font-mono text-[11px] text-muted-foreground">
                        {revealed && stageData?.durationMs ? `${stageData.durationMs}ms` : status}
                      </span>
                    </button>
                    {open && revealed && stageData ? (
                      <div className="px-3 pb-2">
                        <StageFacts stageKey={stage.key} data={stageData} />
                      </div>
                    ) : null}
                  </li>
                );
              })}
            </ol>
          </Panel>
        </div>

        <div className="space-y-3 xl:col-span-3">
          <Panel
            title="gateway.response"
            meta={result ? result.mode : "await"}
            actions={
              result?.response ? (
                <button
                  type="button"
                  onClick={() => {
                    navigator.clipboard.writeText(result.response);
                    setCopied(true);
                    setTimeout(() => setCopied(false), 2000);
                  }}
                  className="inline-flex items-center gap-1 font-mono text-[11px] text-muted-foreground hover:text-foreground"
                >
                  <Copy className="h-3 w-3" aria-hidden="true" />
                  {copied ? "Copied" : "Copy"}
                </button>
              ) : null
            }
          >
            <div className="min-h-[320px] border border-card-border bg-input p-3">
              {!result && !loading && error ? (
                <p className="font-mono text-xs text-destructive">{error}</p>
              ) : null}
              {!result && !loading && !error ? (
                <p className="font-mono text-xs text-muted-foreground">No response. Submit a prompt from request.builder.</p>
              ) : null}
              {loading && !result ? (
                <p className="font-mono text-xs text-muted-foreground">Executing pipeline…</p>
              ) : null}
              {result ? (
                <div className="space-y-3">
                  <p className="font-mono text-[11px] text-muted-foreground uppercase">
                    {result.mode === "live" ? "live gateway" : "mock mode"} · {result.pipeline.router.tierChosen} · {result.pipeline.router.modelChosen}
                    {result.pipeline.cascadeGuard.escalated ? " · escalated" : ""}
                  </p>
                  <pre className="whitespace-pre-wrap font-sans text-sm text-foreground">{result.response}</pre>
                  {result.pipeline.ingress.piiDetected > 0 ? (
                    <p className="border border-card-border bg-card px-2 py-1.5 font-mono text-[11px] text-destructive">
                      PII masked: {result.pipeline.ingress.piiEntities.join(", ")}
                    </p>
                  ) : null}
                </div>
              ) : null}
            </div>
          </Panel>
        </div>
      </div>
    </div>
  );
}
