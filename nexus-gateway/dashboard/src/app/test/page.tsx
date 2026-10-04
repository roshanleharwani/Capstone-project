"use client";

import { useState, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  FlaskConical,
  Send,
  Shield,
  Coins,
  Zap,
  GitMerge,
  CheckCircle2,
  AlertTriangle,
  ChevronRight,
  Copy,
  RotateCcw,
  Cpu,
  Clock,
  Hash,
  DollarSign,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const TENANTS = ["CS Dept", "Bio Dept", "Admin", "Eng Dept"];
const TENANT_COLORS: Record<string, string> = {
  "CS Dept": "bg-blue-500/20 text-blue-400 border-blue-500/30",
  "Bio Dept": "bg-emerald-500/20 text-emerald-400 border-emerald-500/30",
  Admin: "bg-purple-500/20 text-purple-400 border-purple-500/30",
  "Eng Dept": "bg-orange-500/20 text-orange-400 border-orange-500/30",
};

const SAMPLE_PROMPTS = [
  "Explain how the Stackelberg dynamic pricing mechanism works in the NexusGateway token market.",
  "My SSN is 123-45-6789 and email is john.doe@university.edu. Can you summarize the semester?",
  "What is the difference between Tier 1 vLLM inference and Tier 2 OpenAI routing?",
  "sk-abc123XYZ789abc123XYZ789 — please use this API key to run the model evaluation script.",
  "Compress and summarize the following research paper abstract about attention mechanisms in transformers for a non-technical audience.",
];

const PIPELINE_STAGES = [
  { key: "ingress", label: "Ingress Shield", icon: Shield, color: "text-red-400", borderColor: "border-red-500/40" },
  { key: "quota", label: "Quota Market", icon: Coins, color: "text-purple-400", borderColor: "border-purple-500/40" },
  { key: "compression", label: "Context Compression", icon: Zap, color: "text-blue-400", borderColor: "border-blue-500/40" },
  { key: "router", label: "Two-Stage Router", icon: GitMerge, color: "text-emerald-400", borderColor: "border-emerald-500/40" },
  { key: "cascadeGuard", label: "Cascade Guard", icon: CheckCircle2, color: "text-amber-400", borderColor: "border-amber-500/40" },
] as const;

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------
function StatusDot({ status }: { status: PipelineStageData["status"] }) {
  const classes: Record<string, string> = {
    idle: "bg-white/10",
    running: "bg-blue-400 animate-pulse shadow-[0_0_8px_rgba(59,130,246,0.8)]",
    passed: "bg-emerald-400 shadow-[0_0_8px_rgba(16,185,129,0.6)]",
    escalated: "bg-amber-400 shadow-[0_0_8px_rgba(245,158,11,0.6)]",
    failed: "bg-red-400 shadow-[0_0_8px_rgba(239,68,68,0.6)]",
  };
  return <div className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${classes[status]}`} />;
}

function StageDetail({ stageKey, data }: { stageKey: string; data: PipelineStageData }) {
  if (data.status === "idle" || data.status === "running") return null;

  switch (stageKey) {
    case "ingress": {
      const d = data as PipelineResult["pipeline"]["ingress"];
      return (
        <div className="mt-2 space-y-1 text-xs text-muted-foreground">
          <p>PII Entities Found: <span className={d.piiDetected > 0 ? "text-red-400 font-semibold" : "text-emerald-400"}>{d.piiDetected > 0 ? d.piiEntities.join(", ") : "None"}</span></p>
          {d.piiDetected > 0 && (
            <p className="font-mono bg-black/30 rounded p-1 text-[11px] break-all">
              Masked: {d.maskedPrompt.slice(0, 120)}{d.maskedPrompt.length > 120 ? "..." : ""}
            </p>
          )}
        </div>
      );
    }
    case "quota": {
      const d = data as PipelineResult["pipeline"]["quota"];
      return (
        <div className="mt-2 space-y-1 text-xs text-muted-foreground">
          <p>Tenant: <span className="text-white">{d.tenant}</span></p>
          <p>Remaining Quota: <span className="text-emerald-400">{d.quotaRemaining.toLocaleString()} tokens</span></p>
          {d.quotaBorrowed > 0 && <p>Borrowed: <span className="text-amber-400">{d.quotaBorrowed.toLocaleString()} tokens</span></p>}
        </div>
      );
    }
    case "compression": {
      const d = data as PipelineResult["pipeline"]["compression"];
      const pct = (d.compressionRatio * 100).toFixed(1);
      return (
        <div className="mt-2 space-y-2 text-xs text-muted-foreground">
          <div className="flex gap-4">
            <p>Original: <span className="text-white">{d.originalTokens} tok</span></p>
            <p>Compressed: <span className="text-blue-400">{d.compressedTokens} tok</span></p>
            <p>ρ = <span className="text-emerald-400 font-semibold">{pct}%</span></p>
          </div>
          {/* Compression bar */}
          <div className="w-full h-1.5 bg-white/5 rounded-full overflow-hidden">
            <div className="h-full bg-gradient-to-r from-blue-500 to-emerald-500 rounded-full" style={{ width: `${pct}%` }} />
          </div>
          <p>Cache Hit: <span className={d.cacheHit ? "text-emerald-400" : "text-muted-foreground"}>{d.cacheHit ? "Yes ✔" : "No"}</span></p>
        </div>
      );
    }
    case "router": {
      const d = data as PipelineResult["pipeline"]["router"];
      return (
        <div className="mt-2 space-y-1 text-xs text-muted-foreground">
          <p>Predicted ρ: <span className="text-emerald-400 font-semibold">{(d.predictedRho * 100).toFixed(1)}%</span></p>
          <p>Tier: <span className="text-white font-semibold">{d.tierChosen}</span></p>
          <p>Model: <span className="text-blue-400 font-mono">{d.modelChosen}</span></p>
        </div>
      );
    }
    case "cascadeGuard": {
      const d = data as PipelineResult["pipeline"]["cascadeGuard"];
      const pct = Math.floor(d.score * 100);
      return (
        <div className="mt-2 space-y-2 text-xs text-muted-foreground">
          <div className="flex gap-4">
            <p>Score: <span className={d.score >= d.threshold ? "text-emerald-400 font-semibold" : "text-amber-400 font-semibold"}>{d.score.toFixed(3)}</span></p>
            <p>Threshold τ: <span className="text-white">{d.threshold.toFixed(2)}</span></p>
          </div>
          <div className="w-full h-1.5 bg-white/5 rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full ${d.score >= d.threshold ? "bg-emerald-500" : "bg-amber-500"}`}
              style={{ width: `${pct}%` }}
            />
          </div>
          {d.escalated && <p className="text-amber-400">⚠️ Escalated to higher tier (below threshold)</p>}
        </div>
      );
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------
export default function LiveTestPage() {
  const [prompt, setPrompt] = useState("");
  const [tenant, setTenant] = useState("CS Dept");
  const [modelPref, setModelPref] = useState("auto");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<PipelineResult | null>(null);
  const [pipelineProgress, setPipelineProgress] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Animate pipeline stages sequentially on submit
  const animatePipeline = async (finalResult: PipelineResult) => {
    const stageKeys = ["ingress", "quota", "compression", "router", "cascadeGuard"] as const;
    const stageDurations = [
      finalResult.pipeline.ingress.durationMs ?? 80,
      finalResult.pipeline.quota.durationMs ?? 60,
      finalResult.pipeline.compression.durationMs ?? 120,
      finalResult.pipeline.router.durationMs ?? 60,
      finalResult.pipeline.cascadeGuard.durationMs ?? 80,
    ];

    for (let i = 0; i < stageKeys.length; i++) {
      await new Promise((r) => setTimeout(r, Math.min(stageDurations[i] * 3, 350)));
      setPipelineProgress((prev) => [...prev, stageKeys[i]]);
    }
  };

  const handleSubmit = async () => {
    if (!prompt.trim() || loading) return;
    setLoading(true);
    setResult(null);
    setPipelineProgress([]);

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
      const data: PipelineResult = await res.json();
      setResult(data);
      await animatePipeline(data);
    } catch {
      // handle error
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (result?.response) {
      navigator.clipboard.writeText(result.response);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getStageStatus = (key: string): PipelineStageData["status"] => {
    if (!result) return loading ? "running" : "idle";
    if (!pipelineProgress.includes(key)) return loading ? "running" : "idle";
    return (result.pipeline as Record<string, PipelineStageData>)[key]?.status ?? "passed";
  };

  return (
    <div className="space-y-8 pb-10">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-blue-500/20 to-purple-500/20 border border-white/10 flex items-center justify-center">
            <FlaskConical className="w-5 h-5 text-blue-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white">Live Test Console</h1>
            <p className="text-sm text-muted-foreground">Send a prompt through the full NexusGateway pipeline</p>
          </div>
        </div>
        <div className="flex items-center gap-2 text-xs px-3 py-1.5 rounded-full border border-white/10 bg-white/5">
          <div className={`w-2 h-2 rounded-full ${result?.mode === "live" ? "bg-emerald-400" : "bg-amber-400"} ${loading ? "animate-pulse" : ""}`} />
          <span className="text-muted-foreground">
            {loading ? "Processing..." : result?.mode === "live" ? "Live Gateway" : result ? "Mock Mode" : "Ready"}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-5 gap-6">
        {/* LEFT COLUMN: Request Builder + Pipeline Trace */}
        <div className="xl:col-span-2 space-y-6">
          
          {/* Request Builder */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Request Builder</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Tenant Selector */}
              <div>
                <label className="text-xs text-muted-foreground mb-2 block">Tenant</label>
                <div className="flex flex-wrap gap-2">
                  {TENANTS.map((t) => (
                    <button
                      key={t}
                      onClick={() => setTenant(t)}
                      className={`px-3 py-1 rounded-full text-xs font-medium border transition-all ${
                        tenant === t
                          ? TENANT_COLORS[t]
                          : "border-white/10 text-muted-foreground hover:border-white/20 hover:text-white"
                      }`}
                    >
                      {t}
                    </button>
                  ))}
                </div>
              </div>

              {/* Model Preference */}
              <div>
                <label className="text-xs text-muted-foreground mb-2 block">Routing Preference</label>
                <div className="flex gap-2">
                  {["auto", "tier1", "tier2"].map((m) => (
                    <button
                      key={m}
                      onClick={() => setModelPref(m)}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                        modelPref === m
                          ? "bg-primary/20 border-primary/50 text-primary"
                          : "border-white/10 text-muted-foreground hover:border-white/20"
                      }`}
                    >
                      {m === "auto" ? "Auto (Router)" : m === "tier1" ? "Force Tier 1" : "Force Tier 2"}
                    </button>
                  ))}
                </div>
              </div>

              {/* Prompt Input */}
              <div>
                <label className="text-xs text-muted-foreground mb-2 block">Prompt</label>
                <textarea
                  ref={textareaRef}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) handleSubmit();
                  }}
                  rows={6}
                  placeholder="Type your prompt here... (Ctrl+Enter to send)&#10;&#10;Try including an email or SSN to see PII masking in action!"
                  className="w-full bg-black/30 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder:text-muted-foreground/50 outline-none focus:border-primary/50 resize-none font-mono leading-relaxed transition-colors"
                />
              </div>

              {/* Sample Prompts */}
              <div>
                <label className="text-xs text-muted-foreground mb-2 block">Sample Prompts</label>
                <div className="space-y-1.5 max-h-32 overflow-y-auto">
                  {SAMPLE_PROMPTS.map((p, i) => (
                    <button
                      key={i}
                      onClick={() => setPrompt(p)}
                      className="w-full text-left text-xs text-muted-foreground hover:text-white px-3 py-2 rounded-lg hover:bg-white/5 border border-transparent hover:border-white/10 transition-all truncate"
                    >
                      <ChevronRight className="w-3 h-3 inline mr-1 flex-shrink-0" />
                      {p}
                    </button>
                  ))}
                </div>
              </div>

              {/* Submit Button */}
              <button
                id="live-test-submit-btn"
                onClick={handleSubmit}
                disabled={loading || !prompt.trim()}
                className="w-full py-3 rounded-xl bg-gradient-to-r from-primary to-secondary text-white font-semibold text-sm flex items-center justify-center gap-2 hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed transition-all shadow-[0_0_20px_-5px_rgba(59,130,246,0.4)]"
              >
                {loading ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    Processing Pipeline...
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    Send Through Pipeline
                  </>
                )}
              </button>

              {result && (
                <button
                  onClick={() => { setResult(null); setPipelineProgress([]); setPrompt(""); }}
                  className="w-full py-2 rounded-xl border border-white/10 text-muted-foreground text-xs hover:text-white hover:border-white/20 flex items-center justify-center gap-2 transition-all"
                >
                  <RotateCcw className="w-3 h-3" /> Clear & Reset
                </button>
              )}
            </CardContent>
          </Card>

          {/* Pipeline Trace */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Pipeline Trace</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {PIPELINE_STAGES.map((stage, idx) => {
                  const stageData = result?.pipeline[stage.key as keyof PipelineResult["pipeline"]] as PipelineStageData | undefined;
                  const status = getStageStatus(stage.key);
                  const isVisible = pipelineProgress.includes(stage.key);
                  const isActive = loading && !isVisible && pipelineProgress.length === idx;

                  return (
                    <div key={stage.key}>
                      <motion.div
                        initial={{ opacity: 0.4 }}
                        animate={isVisible ? { opacity: 1 } : isActive ? { opacity: [0.4, 0.9, 0.4] } : { opacity: 0.4 }}
                        transition={isActive ? { duration: 1, repeat: Infinity } : { duration: 0.4 }}
                        className={`p-3 rounded-xl border transition-all ${
                          isVisible
                            ? status === "escalated"
                              ? "bg-amber-500/5 border-amber-500/30"
                              : "bg-emerald-500/5 border-emerald-500/20"
                            : isActive
                            ? "bg-blue-500/5 border-blue-500/30"
                            : `bg-white/2 ${stage.borderColor}`
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <StatusDot status={isActive ? "running" : isVisible ? (stageData?.status ?? "passed") : "idle"} />
                          <stage.icon className={`w-4 h-4 ${stage.color}`} />
                          <span className="text-sm font-medium text-white flex-1">{stage.label}</span>
                          {isVisible && stageData?.durationMs && (
                            <span className="text-xs text-muted-foreground">{stageData.durationMs}ms</span>
                          )}
                          {isActive && (
                            <span className="text-xs text-blue-400 animate-pulse">Running...</span>
                          )}
                        </div>
                        <AnimatePresence>
                          {isVisible && stageData && (
                            <motion.div
                              initial={{ opacity: 0, height: 0 }}
                              animate={{ opacity: 1, height: "auto" }}
                              exit={{ opacity: 0, height: 0 }}
                              transition={{ duration: 0.3 }}
                            >
                              <StageDetail stageKey={stage.key} data={stageData} />
                            </motion.div>
                          )}
                        </AnimatePresence>
                      </motion.div>

                      {/* Connector line */}
                      {idx < PIPELINE_STAGES.length - 1 && (
                        <div className="flex justify-center">
                          <div className={`w-px h-3 transition-colors ${
                            isVisible ? "bg-white/20" : "bg-white/5"
                          }`} />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* RIGHT COLUMN: Response + Metrics */}
        <div className="xl:col-span-3 space-y-6">
          
          {/* Metrics Strip */}
          <AnimatePresence>
            {result && (
              <motion.div
                initial={{ opacity: 0, y: -16 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -16 }}
                className="grid grid-cols-2 sm:grid-cols-4 gap-4"
              >
                {[
                  { icon: Clock, label: "Latency", value: `${result.metrics.totalLatencyMs}ms`, color: "text-blue-400" },
                  { icon: Hash, label: "Tokens In / Out", value: `${result.metrics.tokensIn} / ${result.metrics.tokensOut}`, color: "text-purple-400" },
                  { icon: Zap, label: "Compression ρ", value: `${(result.pipeline.compression.compressionRatio * 100).toFixed(1)}%`, color: "text-emerald-400" },
                  { icon: DollarSign, label: "Est. Cost", value: `$${result.metrics.estimatedCostUsd}`, color: "text-amber-400" },
                ].map(({ icon: Icon, label, value, color }) => (
                  <Card key={label} className="!py-3">
                    <CardContent className="flex items-center gap-3 p-0 px-4">
                      <Icon className={`w-5 h-5 ${color} flex-shrink-0`} />
                      <div>
                        <p className="text-xs text-muted-foreground">{label}</p>
                        <p className="text-sm font-bold text-white">{value}</p>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </motion.div>
            )}
          </AnimatePresence>

          {/* Routing Decision Badge */}
          <AnimatePresence>
            {result && pipelineProgress.includes("router") && (
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="flex items-center gap-3 p-4 rounded-xl border border-white/10 bg-white/2"
              >
                <Cpu className="w-5 h-5 text-emerald-400" />
                <div className="flex-1">
                  <p className="text-xs text-muted-foreground">Routing Decision</p>
                  <p className="text-sm font-semibold text-white">{result.pipeline.router.tierChosen}</p>
                  <p className="text-xs text-muted-foreground font-mono">{result.pipeline.router.modelChosen}</p>
                </div>
                {result.pipeline.cascadeGuard.escalated && (
                  <div className="flex items-center gap-1.5 text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 px-3 py-1.5 rounded-lg">
                    <AlertTriangle className="w-3.5 h-3.5" />
                    Cascade Escalated
                  </div>
                )}
                {!result.pipeline.cascadeGuard.escalated && pipelineProgress.includes("cascadeGuard") && (
                  <div className="flex items-center gap-1.5 text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-3 py-1.5 rounded-lg">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    Guard Passed
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>

          {/* Response Panel */}
          <Card className="flex-1">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">Gateway Response</CardTitle>
              {result?.response && (
                <button
                  onClick={handleCopy}
                  className="text-xs text-muted-foreground hover:text-white flex items-center gap-1.5 transition-colors"
                >
                  <Copy className="w-3.5 h-3.5" />
                  {copied ? "Copied!" : "Copy"}
                </button>
              )}
            </CardHeader>
            <CardContent>
              <div className="min-h-[320px] rounded-xl bg-black/30 border border-white/5 p-4">
                <AnimatePresence mode="wait">
                  {!result && !loading && (
                    <motion.div
                      key="empty"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      className="h-full flex flex-col items-center justify-center text-center py-16"
                    >
                      <div className="w-16 h-16 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-4">
                        <FlaskConical className="w-8 h-8 text-muted-foreground" />
                      </div>
                      <p className="text-sm text-muted-foreground">Enter a prompt and click <strong className="text-white">Send Through Pipeline</strong></p>
                      <p className="text-xs text-muted-foreground/60 mt-1">The pipeline trace on the left will animate as each module processes your request</p>
                    </motion.div>
                  )}

                  {loading && !result && (
                    <motion.div
                      key="loading"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      className="h-full flex flex-col items-center justify-center py-16 gap-4"
                    >
                      <div className="relative w-16 h-16">
                        <div className="absolute inset-0 rounded-full border-2 border-primary/20" />
                        <div className="absolute inset-0 rounded-full border-2 border-t-primary animate-spin" />
                        <div className="absolute inset-2 rounded-full border-2 border-t-secondary animate-spin" style={{ animationDuration: "1.5s", animationDirection: "reverse" }} />
                      </div>
                      <p className="text-sm text-muted-foreground">Running through pipeline stages...</p>
                    </motion.div>
                  )}

                  {result && (
                    <motion.div
                      key="result"
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      className="space-y-4"
                    >
                      {/* Mode badge */}
                      <div className="flex items-center gap-2">
                        <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${
                          result.mode === "live"
                            ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-400"
                            : "bg-amber-500/10 border-amber-500/30 text-amber-400"
                        }`}>
                          {result.mode === "live" ? "• Live Gateway" : "○ Mock Mode"}
                        </span>
                        {result.mode === "mock" && (
                          <span className="text-xs text-muted-foreground">Set Gateway URL in Settings to connect to live cluster</span>
                        )}
                      </div>

                      {/* Response text */}
                      <pre className="text-sm text-foreground/90 whitespace-pre-wrap font-sans leading-relaxed">
                        {result.response}
                      </pre>

                      {/* PII entities inline callout */}
                      {result.pipeline.ingress.piiDetected > 0 && (
                        <div className="flex items-start gap-2 p-3 rounded-lg bg-red-500/5 border border-red-500/20">
                          <Shield className="w-4 h-4 text-red-400 flex-shrink-0 mt-0.5" />
                          <div>
                            <p className="text-xs font-semibold text-red-400">PII Masked Before Forwarding</p>
                            <p className="text-xs text-muted-foreground">
                              {result.pipeline.ingress.piiEntities.join(", ")} detected and replaced with session-stable placeholders
                            </p>
                          </div>
                        </div>
                      )}
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
