"use client";

import { Panel } from "@/components/ui/Panel";
import { KpiStrip } from "@/components/ui/KpiStrip";
import { DataTable } from "@/components/ui/DataTable";
import { StatusCell } from "@/components/ui/StatusCell";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { SeriesLegend } from "@/components/charts/SeriesLegend";
import { LiveControls } from "@/components/ui/LiveControls";
import { PageHeader } from "@/components/ui/PageHeader";
import { useLivePoll } from "@/hooks/useLivePoll";
import { useChartPalette } from "@/hooks/useChartPalette";
import { useMemo, useState } from "react";
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, ResponsiveContainer,
  LineChart, Line, Tooltip,
} from "recharts";

type Threat = { id: string; severity: string; type: string; action: string; time: string };
type PiiPayload = {
  interceptionData: { name: string; blocks: number }[];
  riskTrendData: { time: string; high: number; med: number; low: number }[];
  recentThreats: Threat[];
  totalInterceptions: number;
  promptInjectionsBlocked: number;
  entitiesPseudonymized: number;
};

const SAMPLE_THREATS: Threat[] = [
  { id: "thr-1042", severity: "Critical", type: "Prompt injection", action: "blocked", time: "14:01:12" },
  { id: "thr-1041", severity: "High", type: "SSN", action: "masked", time: "14:00:44" },
  { id: "thr-1038", severity: "Medium", type: "Email", action: "masked", time: "13:58:09" },
  { id: "thr-1033", severity: "High", type: "API key", action: "blocked", time: "13:52:17" },
  { id: "thr-1029", severity: "Critical", type: "Prompt injection", action: "blocked", time: "13:41:02" },
];

const initial: PiiPayload = {
  interceptionData: [
    { name: "Regex", blocks: 18 },
    { name: "Secret", blocks: 7 },
    { name: "NER", blocks: 11 },
    { name: "Injection", blocks: 4 },
  ],
  riskTrendData: [
    { time: "Mon", high: 4, med: 8, low: 12 },
    { time: "Tue", high: 6, med: 5, low: 9 },
    { time: "Wed", high: 3, med: 7, low: 14 },
    { time: "Thu", high: 8, med: 6, low: 10 },
    { time: "Fri", high: 5, med: 9, low: 11 },
  ],
  recentThreats: SAMPLE_THREATS,
  totalInterceptions: 0,
  promptInjectionsBlocked: 0,
  entitiesPseudonymized: 0,
};

function severityTone(severity: string) {
  if (severity === "Critical") return "danger" as const;
  if (severity === "High") return "warning" as const;
  if (severity === "Medium") return "info" as const;
  return "success" as const;
}

export default function PIISecurity() {
  const { data, status, paused, setPaused, updatedAt, refresh } = useLivePoll(
    "/api/v1/dashboard/pii",
    initial
  );
  const palette = useChartPalette();
  const [sev, setSev] = useState<"all" | "Critical" | "High" | "Medium">("all");
  const [engine, setEngine] = useState("");
  const [hidden, setHidden] = useState<Record<string, boolean>>({});
  const source = data.recentThreats.length ? data.recentThreats : SAMPLE_THREATS;
  const threats = useMemo(() => {
    return source.filter((row) => {
      if (sev !== "all" && row.severity !== sev) return false;
      if (engine === "inj" && row.type !== "Prompt injection") return false;
      if (engine === "ent" && !/SSN|Email|API/i.test(row.type)) return false;
      return true;
    });
  }, [engine, sev, source]);

  return (
    <div className="space-y-4 pb-8">
      <PageHeader
        title="PII"
        crumbs={["prod", "nexus-gw", "control", "pii"]}
        actions={
          <LiveControls
            paused={paused}
            onToggle={() => setPaused((value) => !value)}
            onRefresh={() => void refresh()}
            updatedAt={updatedAt}
            status={status}
          />
        }
      />

      <KpiStrip
        selectedId={engine}
        onSelect={(id) => {
          setEngine(id);
          if (id === "inj") setSev("Critical");
          if (!id) setSev("all");
        }}
        items={[
          { id: "int", label: "Interceptions", value: data.totalInterceptions.toLocaleString(), tone: "success" },
          { id: "inj", label: "Injections blocked", value: data.promptInjectionsBlocked.toLocaleString(), hint: engine === "inj" ? "injection only" : "click to filter", tone: "danger" },
          { id: "ent", label: "Entities masked", value: data.entitiesPseudonymized.toLocaleString(), hint: engine === "ent" ? "PII entities" : "click to filter", tone: "info" },
          { id: "log", label: "Threat rows", value: String(source.length), tone: "neutral" },
        ]}
      />

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <Panel title="scanner.blocks" meta="engine">
          <div className="h-[240px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.interceptionData} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={palette.grid} horizontal={false} />
                <XAxis type="number" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <YAxis type="category" dataKey="name" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} width={90} />
                <Tooltip cursor={{ fill: palette.cursor, opacity: 0.35 }} content={<ChartTooltip />} />
                <Bar dataKey="blocks" name="Blocks" fill="#f85149" maxBarSize={14} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Panel>

        <Panel title="risk.trend" meta="5d · toggle series">
          <SeriesLegend
            hidden={hidden}
            onToggle={(id) => setHidden((prev) => ({ ...prev, [id]: !prev[id] }))}
            series={[
              { id: "high", label: "High", color: "#f85149" },
              { id: "med", label: "Med", color: "#d29922", dashed: true },
              { id: "low", label: "Low", color: "#3fb950", dashed: true },
            ]}
          />
          <div className="h-[240px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data.riskTrendData} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={palette.grid} vertical={false} />
                <XAxis dataKey="time" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <YAxis stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <Tooltip content={<ChartTooltip />} />
                {!hidden.high ? <Line type="monotone" dataKey="high" name="High" stroke="#f85149" strokeWidth={1.5} dot={false} /> : null}
                {!hidden.med ? <Line type="monotone" dataKey="med" name="Med" stroke="#d29922" strokeWidth={1.5} strokeDasharray="4 3" dot={false} /> : null}
                {!hidden.low ? <Line type="monotone" dataKey="low" name="Low" stroke="#3fb950" strokeWidth={1.5} strokeDasharray="2 3" dot={false} /> : null}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>

      <Panel
        title="threat.log"
        meta="ingress"
        actions={
          <SegmentedControl
            label="Severity filter"
            value={sev}
            onChange={setSev}
            options={[
              { id: "all", label: "All" },
              { id: "Critical", label: "Crit" },
              { id: "High", label: "High" },
              { id: "Medium", label: "Med" },
            ]}
          />
        }
      >
        <DataTable
          filterPlaceholder="Filter threats"
          empty="No threats in this window"
          rowKey={(row) => row.id}
          rows={threats}
          copyValue={(row) => `${row.id}\t${row.severity}\t${row.type}\t${row.action}\t${row.time}`}
          renderExpanded={(row) => (
            <p>
              {row.id} · {row.severity} · {row.type}
              <br />
              action={row.action} at {row.time}
            </p>
          )}
          columns={[
            {
              id: "sev",
              header: "Sev",
              value: (row) => row.severity,
              render: (row) => <StatusCell label={row.severity} tone={severityTone(row.severity)} />,
            },
            { id: "type", header: "Type", value: (row) => row.type },
            { id: "id", header: "ID", mono: true, value: (row) => row.id },
            { id: "action", header: "Action", value: (row) => row.action },
            { id: "time", header: "Time", align: "right", mono: true, value: (row) => row.time },
          ]}
        />
      </Panel>
    </div>
  );
}
