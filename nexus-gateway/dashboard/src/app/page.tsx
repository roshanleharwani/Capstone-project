"use client";

import { Panel } from "@/components/ui/Panel";
import { KpiStrip } from "@/components/ui/KpiStrip";
import { DataTable } from "@/components/ui/DataTable";
import { StatusCell } from "@/components/ui/StatusCell";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { SeriesLegend } from "@/components/charts/SeriesLegend";
import { LiveControls } from "@/components/ui/LiveControls";
import { PageHeader } from "@/components/ui/PageHeader";
import { useLivePoll } from "@/hooks/useLivePoll";
import { useChartPalette } from "@/hooks/useChartPalette";
import { useMemo, useState } from "react";
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, ResponsiveContainer,
  BarChart, Bar, Tooltip, Brush,
} from "recharts";

type ActivityItem = { type?: string; event: string; time: string };
type DashboardPayload = {
  routerData: { time: string; tier1: number; tier2: number }[];
  compressionData: { name: string; original: number; compressed: number }[];
  metrics: { title: string; value: string; change: string; positive: boolean }[];
  activityFeed: ActivityItem[];
};

const SAMPLE_EVENTS: ActivityItem[] = [
  { type: "success", event: "cache.hit tenant=CS Dept key=sem:a91c", time: "14:02:11" },
  { type: "info", event: "router.tier1 model=llama-3-8b tokens=412", time: "14:02:09" },
  { type: "warning", event: "pii.mask EMAIL tenant=Admin", time: "14:01:44" },
  { type: "danger", event: "pii.block prompt_injection tenant=Bio Dept", time: "14:01:12" },
  { type: "info", event: "quota.borrow CS Dept <- Eng Dept 12k tok", time: "14:00:58" },
  { type: "success", event: "cache.store vector_id=v-8841", time: "14:00:41" },
  { type: "warning", event: "quota.util 78% surplus pool thinning", time: "13:59:02" },
];

const initialData: DashboardPayload = {
  routerData: [
    { time: "08:00", tier1: 2100, tier2: 400 },
    { time: "09:00", tier1: 2800, tier2: 620 },
    { time: "10:00", tier1: 3400, tier2: 900 },
    { time: "11:00", tier1: 3100, tier2: 1100 },
    { time: "12:00", tier1: 2600, tier2: 700 },
    { time: "13:00", tier1: 3900, tier2: 1400 },
    { time: "14:00", tier1: 4200, tier2: 1600 },
  ],
  compressionData: [
    { name: "CS Dept", original: 100, compressed: 41 },
    { name: "Bio Dept", original: 100, compressed: 55 },
    { name: "Admin", original: 100, compressed: 38 },
    { name: "Eng Dept", original: 100, compressed: 62 },
  ],
  metrics: [
    { title: "Total Cost Savings", value: "$0.00", change: "+0%", positive: true },
    { title: "Cache Hit Ratio", value: "0%", change: "+0%", positive: true },
    { title: "PII Intercepts", value: "0", change: "-0%", positive: false },
    { title: "Market Quota Traded", value: "0M", change: "+0%", positive: true },
  ],
  activityFeed: [],
};

function activityTone(type?: string) {
  if (type === "warning") return "warning" as const;
  if (type === "danger") return "danger" as const;
  if (type === "success") return "success" as const;
  return "info" as const;
}

export default function Dashboard() {
  const { data, status, paused, setPaused, updatedAt, refresh } = useLivePoll(
    "/api/v1/dashboard/metrics",
    initialData
  );
  const palette = useChartPalette();
  const [kpi, setKpi] = useState("");
  const [hidden, setHidden] = useState<Record<string, boolean>>({});
  const [barHidden, setBarHidden] = useState<Record<string, boolean>>({});

  const events = useMemo(() => {
    const rows = data.activityFeed.length ? data.activityFeed : SAMPLE_EVENTS;
    if (kpi === "Cache Hit Ratio") return rows.filter((row) => /cache/i.test(row.event));
    if (kpi === "PII Intercepts") return rows.filter((row) => /pii|inject/i.test(row.event) || row.type === "danger");
    if (kpi === "Market Quota Traded") return rows.filter((row) => /quota|borrow/i.test(row.event));
    return rows;
  }, [data.activityFeed, kpi]);

  const live = (
    <LiveControls
      paused={paused}
      onToggle={() => setPaused((value) => !value)}
      onRefresh={() => void refresh()}
      updatedAt={updatedAt}
      status={status}
    />
  );

  return (
    <div className="space-y-4 pb-8">
      <PageHeader title="Overview" crumbs={["prod", "nexus-gw", "observe", "overview"]} actions={live} />

      <KpiStrip
        selectedId={kpi}
        onSelect={setKpi}
        items={data.metrics.map((metric) => ({
          id: metric.title,
          label: metric.title,
          value: metric.value,
          hint: kpi === metric.title ? "filtering log" : metric.change,
          tone: metric.positive ? "success" : "danger",
        }))}
      />

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-5">
        <Panel title="router.tier_mix" meta="drag brush to zoom" className="xl:col-span-3">
          <SeriesLegend
            hidden={hidden}
            onToggle={(id) => setHidden((prev) => ({ ...prev, [id]: !prev[id] }))}
            series={[
              { id: "tier1", label: "T1 Bedrock", color: "#4493f8" },
              { id: "tier2", label: "T2 OpenAI", color: "#3fb950", dashed: true },
            ]}
          />
          <div className="h-[280px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={data.routerData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <XAxis dataKey="time" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <YAxis stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} tickFormatter={(value) => `${value / 1000}k`} />
                <CartesianGrid strokeDasharray="2 4" stroke={palette.grid} vertical={false} />
                <Tooltip content={<ChartTooltip />} />
                {!hidden.tier1 ? (
                  <Area type="monotone" dataKey="tier1" name="T1 Bedrock" stroke="#4493f8" strokeWidth={1.5} fill="#4493f8" fillOpacity={0.12} />
                ) : null}
                {!hidden.tier2 ? (
                  <Area type="monotone" dataKey="tier2" name="T2 OpenAI" stroke="#3fb950" strokeWidth={1.5} strokeDasharray="4 3" fill="#3fb950" fillOpacity={0.08} />
                ) : null}
                <Brush dataKey="time" height={16} stroke={palette.axis} fill={palette.cursor} travellerWidth={8} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Panel>

        <Panel title="compression.by_tenant" meta="tokens" className="xl:col-span-2">
          <SeriesLegend
            hidden={barHidden}
            onToggle={(id) => setBarHidden((prev) => ({ ...prev, [id]: !prev[id] }))}
            series={[
              { id: "original", label: "Original", color: "#656d76" },
              { id: "compressed", label: "Compressed", color: "#3fb950" },
            ]}
          />
          <div className="h-[280px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.compressionData} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={palette.grid} vertical={false} />
                <XAxis dataKey="name" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <YAxis stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <Tooltip content={<ChartTooltip />} />
                {!barHidden.original ? <Bar dataKey="original" name="Original" fill="#656d76" maxBarSize={18} /> : null}
                {!barHidden.compressed ? <Bar dataKey="compressed" name="Compressed" fill="#3fb950" maxBarSize={18} /> : null}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>

      <Panel title="event.log" meta={data.activityFeed.length ? "kafka" : "sample window"}>
        <DataTable
          filterPlaceholder="Filter events"
          empty="No events in the current window"
          rowKey={(row) => `${row.time}-${row.event}`}
          rows={events}
          copyValue={(row) => `${row.time}\t${row.type ?? "info"}\t${row.event}`}
          renderExpanded={(row) => (
            <p>
              {row.time} · sev={row.type ?? "info"}
              <br />
              {row.event}
            </p>
          )}
          columns={[
            {
              id: "sev",
              header: "Sev",
              value: (row) => row.type ?? "info",
              render: (row) => <StatusCell label={row.type ?? "info"} tone={activityTone(row.type)} />,
            },
            { id: "event", header: "Event", value: (row) => row.event },
            { id: "time", header: "Time", align: "right", mono: true, value: (row) => row.time },
          ]}
        />
      </Panel>
    </div>
  );
}
