"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Settings, Shield, Zap, Coins, Sliders, Globe, CheckCircle2 } from "lucide-react";
import { useState } from "react";
import { motion } from "framer-motion";

  import { useEffect } from "react";

  export default function SettingsPage() {
    const [settings, setSettings] = useState({
      regexScanner: true,
      secretScanner: true,
      nerScanner: true,
      cascadeGuard: true,
      stackelberg: true,
      cacheEnabled: true
    });
    const [gatewayUrl, setGatewayUrl] = useState("");
    const [savedUrl, setSavedUrl] = useState("");
    const [urlSaved, setUrlSaved] = useState(false);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
      const storedUrl = localStorage.getItem("NEXUS_GATEWAY_URL") || "";
      if (storedUrl) {
        setGatewayUrl(storedUrl);
        setSavedUrl(storedUrl);
      }
      fetch(`/internal/settings?gateway=${encodeURIComponent(storedUrl)}&t=${Date.now()}`)
        .then(res => res.json())
        .then(data => {
            if (data.regexScanner !== undefined) {
                setSettings(data);
            }
        });
    }, []);

    const handleSaveGatewayUrl = () => {
      setSavedUrl(gatewayUrl);
      // Store in localStorage so the API route can pick it up via client-side calls
      if (typeof window !== "undefined") {
        localStorage.setItem("NEXUS_GATEWAY_URL", gatewayUrl);
      }
      setUrlSaved(true);
      setTimeout(() => setUrlSaved(false), 2500);
    };

    const toggle = (key: keyof typeof settings) => {
      setSettings(prev => ({ ...prev, [key]: !prev[key] }));
    };

    const saveSettings = async () => {
        setIsSaving(true);
        const url = localStorage.getItem("NEXUS_GATEWAY_URL") || "";
        await fetch(`/internal/settings?gateway=${encodeURIComponent(url)}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(settings)
        });
        setTimeout(() => setIsSaving(false), 1000);
    };

  return (
    <div className="space-y-8 pb-10">
      <div className="flex items-center gap-3 mb-6">
        <Settings className="w-8 h-8 text-white" />
        <h1 className="text-3xl font-bold text-white">Gateway Configuration</h1>
      </div>

      {/* Gateway Connection Card (full-width) */}
      <Card className="border-primary/20 bg-primary/5">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Globe className="w-5 h-5 text-primary" />
            Gateway Connection
          </CardTitle>
          <p className="text-sm text-muted-foreground">Set the NexusGateway API base URL for the Live Test console</p>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex gap-3 items-end">
            <div className="flex-1 space-y-1">
              <label className="text-xs text-muted-foreground">Gateway Base URL</label>
              <input
                id="gateway-url-input"
                type="text"
                value={gatewayUrl}
                onChange={(e) => setGatewayUrl(e.target.value)}
                placeholder="http://<LoadBalancer-IP>:8080  or  https://nexus.yourdomain.com"
                className="w-full bg-black/30 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder:text-muted-foreground/50 outline-none focus:border-primary/50 font-mono transition-colors"
              />
            </div>
            <button
              id="save-gateway-url-btn"
              onClick={handleSaveGatewayUrl}
              className="px-5 py-3 rounded-xl bg-gradient-to-r from-primary to-secondary text-white text-sm font-semibold hover:opacity-90 transition-all flex items-center gap-2 flex-shrink-0"
            >
              {urlSaved ? <><CheckCircle2 className="w-4 h-4" /> Saved!</> : "Save"}
            </button>
          </div>
          {savedUrl && (
            <p className="text-xs text-emerald-400">
              ✓ Connected to: <span className="font-mono">{savedUrl}</span> — Live Test will now route to the real gateway.
            </p>
          )}
          {!savedUrl && (
            <p className="text-xs text-muted-foreground">
              Leave empty to run in <span className="text-amber-400">Mock Mode</span> — the Live Test page will simulate realistic pipeline responses without a live cluster.
            </p>
          )}
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Security Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Shield className="w-5 h-5 text-destructive" />
              Ingress Privacy Shield
            </CardTitle>
            <p className="text-sm text-muted-foreground">Configure PII masking and prompt injection filters</p>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold text-white">Regex Scanner</div>
                <div className="text-sm text-muted-foreground">Pattern-based SSN & Credit Card masking</div>
              </div>
              <button 
                onClick={() => toggle('regexScanner')}
                className={`w-12 h-6 rounded-full transition-colors relative ${settings.regexScanner ? 'bg-primary' : 'bg-white/10'}`}
              >
                <motion.div layout className={`w-5 h-5 rounded-full bg-white absolute top-0.5 ${settings.regexScanner ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>
            
            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold text-white">Secret Scanner</div>
                <div className="text-sm text-muted-foreground">Entropy-based API key & token detection</div>
              </div>
              <button 
                onClick={() => toggle('secretScanner')}
                className={`w-12 h-6 rounded-full transition-colors relative ${settings.secretScanner ? 'bg-primary' : 'bg-white/10'}`}
              >
                <motion.div layout className={`w-5 h-5 rounded-full bg-white absolute top-0.5 ${settings.secretScanner ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>

            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold text-white">Transformer NER Scanner</div>
                <div className="text-sm text-muted-foreground">Context-aware entity masking (Names, Orgs)</div>
              </div>
              <button 
                onClick={() => toggle('nerScanner')}
                className={`w-12 h-6 rounded-full transition-colors relative ${settings.nerScanner ? 'bg-primary' : 'bg-white/10'}`}
              >
                <motion.div layout className={`w-5 h-5 rounded-full bg-white absolute top-0.5 ${settings.nerScanner ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>
          </CardContent>
        </Card>

        {/* Router Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sliders className="w-5 h-5 text-accent" />
              Two-Stage Router & Execution
            </CardTitle>
            <p className="text-sm text-muted-foreground">Configure routing thresholds and FrugalGPT fallback</p>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-2">
              <div className="flex justify-between">
                <label className="font-semibold text-white text-sm">Cascade Guard Reliability Threshold (τ)</label>
                <span className="text-xs text-accent bg-accent/20 px-2 py-0.5 rounded">0.85</span>
              </div>
              <input type="range" min="0" max="100" defaultValue="85" className="w-full accent-accent" />
              <p className="text-xs text-muted-foreground">If model confidence drops below 0.85, the gateway will escalate to a higher tier model.</p>
            </div>

            <div className="flex items-center justify-between pt-2">
              <div>
                <div className="font-semibold text-white">Enable Cascade Guard</div>
                <div className="text-sm text-muted-foreground">Allow automatic fallback retries</div>
              </div>
              <button 
                onClick={() => toggle('cascadeGuard')}
                className={`w-12 h-6 rounded-full transition-colors relative ${settings.cascadeGuard ? 'bg-primary' : 'bg-white/10'}`}
              >
                <motion.div layout className={`w-5 h-5 rounded-full bg-white absolute top-0.5 ${settings.cascadeGuard ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>
          </CardContent>
        </Card>

        {/* Cache Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Zap className="w-5 h-5 text-primary" />
              Semantic Shadow Cache
            </CardTitle>
            <p className="text-sm text-muted-foreground">Vector retrieval and context compression</p>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold text-white">Enable Semantic Cache</div>
                <div className="text-sm text-muted-foreground">Intercept queries via pgvector similarity</div>
              </div>
              <button 
                onClick={() => toggle('cacheEnabled')}
                className={`w-12 h-6 rounded-full transition-colors relative ${settings.cacheEnabled ? 'bg-primary' : 'bg-white/10'}`}
              >
                <motion.div layout className={`w-5 h-5 rounded-full bg-white absolute top-0.5 ${settings.cacheEnabled ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>
            
            <div className="space-y-2">
              <div className="flex justify-between">
                <label className="font-semibold text-white text-sm">Similarity Threshold (Top-K MMR)</label>
                <span className="text-xs text-primary bg-primary/20 px-2 py-0.5 rounded">0.92</span>
              </div>
              <input type="range" min="0" max="100" defaultValue="92" className="w-full accent-primary" />
            </div>
          </CardContent>
        </Card>

        {/* Market Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Coins className="w-5 h-5 text-secondary" />
              Market Incentives
            </CardTitle>
            <p className="text-sm text-muted-foreground">Quota pools and VCG auction solver</p>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold text-white">Stackelberg Pricing Engine</div>
                <div className="text-sm text-muted-foreground">Dynamically calculate exchange rates</div>
              </div>
              <button 
                onClick={() => toggle('stackelberg')}
                className={`w-12 h-6 rounded-full transition-colors relative ${settings.stackelberg ? 'bg-primary' : 'bg-white/10'}`}
              >
                <motion.div layout className={`w-5 h-5 rounded-full bg-white absolute top-0.5 ${settings.stackelberg ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>

            <div className="space-y-2 pt-2">
              <label className="font-semibold text-white text-sm">Global System Pool Cap</label>
              <div className="flex items-center gap-3">
                <input type="number" defaultValue="500000" className="bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-white text-sm w-full outline-none focus:border-secondary" />
                <span className="text-sm text-muted-foreground">tokens</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
      
      <div className="flex justify-end pt-4">
        <button 
          onClick={saveSettings}
          disabled={isSaving}
          className="px-6 py-2 bg-gradient-to-r from-primary to-secondary text-white font-semibold rounded-lg shadow-glow-primary hover:opacity-90 transition-opacity disabled:opacity-50"
        >
          {isSaving ? "Saving..." : "Save Configuration"}
        </button>
      </div>
    </div>
  );
}
