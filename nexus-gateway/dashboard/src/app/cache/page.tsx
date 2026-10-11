"use client";

import { Panel } from "@/components/ui/Panel";
import { KpiStrip } from "@/components/ui/KpiStrip";
import { DataTable } from "@/components/ui/DataTable";
import { ChartTooltip } from "@/components/charts/ChartTooltip";
import { SeriesLegend } from "@/components/charts/SeriesLegend";
import { LiveControls } from "@/components/ui/LiveControls";
import { PageHeader } from "@/components/ui/PageHeader";
import { useLivePoll } from "@/hooks/useLivePoll";
import { useChartPalette } from "@/hooks/useChartPalette";
import { useMemo, useState } from "react";
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Tooltip, Brush,
} from "recharts";

type CachePayload = {
  cacheHistory: { time: string; hits: number; misses: number }[];
  evictionData: { name: string; value: number }[];
  totalHits: number;
  latencySaved: string;
  vectorStoreSize: string;
};

const SAMPLE_EVICT = [
  { name: "TTL", value: 42 },
  { name: "LRU", value: 28 },
  { name: "MMR miss", value: 17 },
  { name: "Manual", value: 6 },
];

const SAMPLE_HISTORY = [
  { time: "10:00", hits: 120, misses: 18 },
  { time: "11:00", hits: 154, misses: 22 },
  { time: "12:00", hits: 98, misses: 31 },
  { time: "13:00", hits: 176, misses: 14 },
  { time: "14:00", hits: 201, misses: 19 },
];

const initial: CachePayload = {
  cacheHistory: SAMPLE_HISTORY,
  evictionData: SAMPLE_EVICT,
  totalHits: 0,
  latencySaved: "0",
  vectorStoreSize: "0",
};

export default function CacheAnalytics() {
  const { data, status, paused, setPaused, updatedAt, refresh } = useLivePoll(
    "/api/v1/dashboard/cache",
    initial
  );
  const palette = useChartPalette();
  const [kpi, setKpi] = useState("");
  const [hidden, setHidden] = useState<Record<string, boolean>>({});
  const evictions = data.evictionData.length ? data.evictionData : SAMPLE_EVICT;
  const history = data.cacheHistory.length ? data.cacheHistory : SAMPLE_HISTORY;
  const totalEvictions = evictions.reduce((sum, row) => sum + row.value, 0);
  const evictionRows = useMemo(
    () => (kpi === "evict" ? evictions.filter((row) => row.value >= 20) : evictions),
    [evictions, kpi]
  );

  return (
    <div className="space-y-4 pb-8">
      <PageHeader
        title="Cache"
        crumbs={["prod", "nexus-gw", "observe", "cache"]}
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
        selectedId={kpi}
        onSelect={setKpi}
        items={[
          { id: "hits", label: "Cache hits", value: data.totalHits.toLocaleString(), hint: "pgvector", tone: "success" },
          { id: "lat", label: "Latency saved", value: `${data.latencySaved}s`, tone: "info" },
          { id: "store", label: "Vector store", value: `${data.vectorStoreSize} GB`, tone: "neutral" },
          { id: "evict", label: "Evictions", value: String(totalEvictions), hint: kpi === "evict" ? "count ≥ 20" : "click to isolate", tone: "neutral" },
        ]}
      />

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-5">
        <Panel title="cache.throughput" meta="drag brush to zoom" className="xl:col-span-3">
          <SeriesLegend
            hidden={hidden}
            onToggle={(id) => setHidden((prev) => ({ ...prev, [id]: !prev[id] }))}
            series={[
              { id: "hits", label: "Hits", color: "#3fb950" },
              { id: "misses", label: "Misses", color: "#f85149", dashed: true },
            ]}
          />
          <div className="h-[280px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={history} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <XAxis dataKey="time" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <YAxis stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <CartesianGrid strokeDasharray="2 4" stroke={palette.grid} vertical={false} />
                <Tooltip content={<ChartTooltip />} />
                {!hidden.hits ? (
                  <Area type="monotone" dataKey="hits" name="Hits" stroke="#3fb950" strokeWidth={1.5} fill="#3fb950" fillOpacity={0.12} />
                ) : null}
                {!hidden.misses ? (
                  <Area type="monotone" dataKey="misses" name="Misses" stroke="#f85149" strokeWidth={1.5} strokeDasharray="4 3" fill="#f85149" fillOpacity={0.08} />
                ) : null}
                <Brush dataKey="time" height={16} stroke={palette.axis} fill={palette.cursor} travellerWidth={8} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Panel>

        <Panel title="cache.eviction" meta="reason" className="xl:col-span-2">
          <DataTable
            empty="No eviction samples"
            rowKey={(row) => row.name}
            rows={evictionRows}
            copyValue={(row) => `${row.name}\t${row.value}`}
            renderExpanded={(row) => (
              <p>
                {row.name}: {row.value} ({totalEvictions ? Math.round((row.value / totalEvictions) * 100) : 0}% of window)
              </p>
            )}
            columns={[
              { id: "name", header: "Reason", value: (row) => row.name },
              { id: "value", header: "Count", align: "right", mono: true, value: (row) => row.value },
              {
                id: "share",
                header: "Share",
                align: "right",
                mono: true,
                value: (row) => (totalEvictions ? `${Math.round((row.value / totalEvictions) * 100)}%` : "0%"),
                render: (row) => {
                  const pct = totalEvictions ? Math.round((row.value / totalEvictions) * 100) : 0;
                  return (
                    <span className="inline-flex w-full items-center justify-end gap-2">
                      <span className="h-1.5 w-16 overflow-hidden bg-muted">
                        <span className="block h-full bg-primary" style={{ width: `${pct}%` }} />
                      </span>
                      {pct}%
                    </span>
                  );
                },
              },
            ]}
          />
        </Panel>
      </div>
    </div>
  );
}
