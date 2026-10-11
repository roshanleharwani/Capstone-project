"use client";

import { Button } from "@/components/ui/Button";
import { Toggle } from "@/components/ui/Toggle";
import { Panel } from "@/components/ui/Panel";
import { PropertyRow } from "@/components/ui/PropertyRow";
import { PageHeader } from "@/components/ui/PageHeader";
import { CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";

export default function SettingsPage() {
  const [settings, setSettings] = useState({
    regexScanner: true,
    secretScanner: true,
    nerScanner: true,
    cascadeGuard: true,
    stackelberg: true,
    cacheEnabled: true,
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
      .then((res) => res.json())
      .then((data) => {
        if (data.regexScanner !== undefined) {
          setSettings(data);
        }
      });
  }, []);

  const handleSaveGatewayUrl = () => {
    setSavedUrl(gatewayUrl);
    localStorage.setItem("NEXUS_GATEWAY_URL", gatewayUrl);
    setUrlSaved(true);
    setTimeout(() => setUrlSaved(false), 2500);
  };

  const toggle = (key: keyof typeof settings) => {
    setSettings((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const saveSettings = async () => {
    setIsSaving(true);
    const url = localStorage.getItem("NEXUS_GATEWAY_URL") || "";
    await fetch(`/internal/settings?gateway=${encodeURIComponent(url)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(settings),
    });
    setTimeout(() => setIsSaving(false), 1000);
  };

  return (
    <div className="space-y-4 pb-8">
      <PageHeader
        title="Config"
        crumbs={["prod", "nexus-gw", "operate", "config"]}
        actions={
          <Button onClick={() => void saveSettings()} loading={isSaving} disabled={isSaving}>
            {isSaving ? "Writing…" : "Commit"}
          </Button>
        }
      />

      <Panel
        title="endpoint.gateway"
        meta="live-test"
        actions={
          <Button id="save-gateway-url-btn" variant="outline" onClick={handleSaveGatewayUrl}>
            {urlSaved ? (
              <>
                <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" /> Saved
              </>
            ) : (
              "Apply URL"
            )}
          </Button>
        }
      >
        <PropertyRow label="Base URL" htmlFor="gateway-url-input" hint={savedUrl ? `Bound: ${savedUrl}` : "Empty = mock mode"}>
          <input
            id="gateway-url-input"
            type="url"
            value={gatewayUrl}
            onChange={(e) => setGatewayUrl(e.target.value)}
            placeholder="http://<lb-ip>:8080"
            className="h-8 w-full border border-card-border bg-input px-2 font-mono text-xs text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
          />
        </PropertyRow>
      </Panel>

      <Panel title="module.ingress">
        <PropertyRow label="Regex scanner" hint="SSN / PAN patterns">
          <Toggle label="Regex scanner" checked={settings.regexScanner} onChange={() => toggle("regexScanner")} />
        </PropertyRow>
        <PropertyRow label="Secret scanner" hint="High-entropy tokens">
          <Toggle label="Secret scanner" checked={settings.secretScanner} onChange={() => toggle("secretScanner")} />
        </PropertyRow>
        <PropertyRow label="NER scanner" hint="Names / orgs">
          <Toggle label="NER scanner" checked={settings.nerScanner} onChange={() => toggle("nerScanner")} />
        </PropertyRow>
      </Panel>

      <Panel title="module.router">
        <PropertyRow label="Cascade τ" htmlFor="cascade-threshold" hint="Escalate below 0.85">
          <input id="cascade-threshold" type="range" min="0" max="100" defaultValue="85" className="w-full accent-foreground" />
        </PropertyRow>
        <PropertyRow label="Cascade guard">
          <Toggle label="Cascade guard" checked={settings.cascadeGuard} onChange={() => toggle("cascadeGuard")} />
        </PropertyRow>
      </Panel>

      <Panel title="module.cache">
        <PropertyRow label="Semantic cache">
          <Toggle label="Semantic cache" checked={settings.cacheEnabled} onChange={() => toggle("cacheEnabled")} />
        </PropertyRow>
        <PropertyRow label="MMR threshold" htmlFor="similarity-threshold">
          <input id="similarity-threshold" type="range" min="0" max="100" defaultValue="92" className="w-full accent-foreground" />
        </PropertyRow>
      </Panel>

      <Panel title="module.market">
        <PropertyRow label="Stackelberg FX">
          <Toggle label="Stackelberg FX" checked={settings.stackelberg} onChange={() => toggle("stackelberg")} />
        </PropertyRow>
        <PropertyRow label="Pool cap" htmlFor="pool-cap" hint="tokens">
          <input
            id="pool-cap"
            type="number"
            defaultValue="500000"
            className="h-8 w-full max-w-xs border border-card-border bg-input px-2 font-mono text-xs text-foreground outline-none focus:border-primary"
          />
        </PropertyRow>
      </Panel>
    </div>
  );
}
