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
  LineChart, Line, XAxis, YAxis, CartesianGrid, ResponsiveContainer,
  BarChart, Bar, Tooltip, Brush,
} from "recharts";

type Tx = {
  txId: string;
  borrower: string;
  lender: string;
  tokens: number;
  rate: number;
  credits: number;
  time: string;
};

type QuotaPayload = {
  priceTrendData: { time: string; rate: number }[];
  tenantQuotaData: { name: string; used: number; limit: number }[];
  ledgerTransactions: Tx[];
  currentExchangeRate: number;
  systemUtilization: number;
  surplusPoolSize: number;
};

const SAMPLE_TX: Tx[] = [
  { txId: "tx-9f2a", borrower: "CS Dept", lender: "Eng Dept", tokens: 12000, rate: 2.8, credits: 33.6, time: "14:00:58" },
  { txId: "tx-9f19", borrower: "Bio Dept", lender: "Admin", tokens: 8000, rate: 3.1, credits: 24.8, time: "13:44:12" },
  { txId: "tx-9eec", borrower: "Admin", lender: "Eng Dept", tokens: 4500, rate: 2.6, credits: 11.7, time: "13:21:03" },
  { txId: "tx-9ed1", borrower: "CS Dept", lender: "Bio Dept", tokens: 16000, rate: 2.9, credits: 46.4, time: "12:58:40" },
];

const initial: QuotaPayload = {
  priceTrendData: [
    { time: "10:00", rate: 2.4 },
    { time: "11:00", rate: 2.6 },
    { time: "12:00", rate: 2.9 },
    { time: "13:00", rate: 2.7 },
    { time: "14:00", rate: 2.8 },
  ],
  tenantQuotaData: [
    { name: "CS Dept", used: 72000, limit: 100000 },
    { name: "Bio Dept", used: 41000, limit: 80000 },
    { name: "Admin", used: 18000, limit: 50000 },
    { name: "Eng Dept", used: 56000, limit: 120000 },
  ],
  ledgerTransactions: SAMPLE_TX,
  currentExchangeRate: 2.8,
  systemUtilization: 0,
  surplusPoolSize: 0,
};

export default function MarketQuota() {
  const { data, status, paused, setPaused, updatedAt, refresh } = useLivePoll(
    "/api/v1/dashboard/quota",
    initial
  );
  const palette = useChartPalette();
  const [tenant, setTenant] = useState("");
  const [hidden, setHidden] = useState<Record<string, boolean>>({});
  const source = data.ledgerTransactions.length ? data.ledgerTransactions : SAMPLE_TX;
  const rows = useMemo(
    () => (tenant ? source.filter((row) => row.borrower === tenant || row.lender === tenant) : source),
    [source, tenant]
  );

  return (
    <div className="space-y-4 pb-8">
      <PageHeader
        title="Quota"
        crumbs={["prod", "nexus-gw", "control", "quota"]}
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
        selectedId={tenant ? "tx" : ""}
        onSelect={(id) => {
          if (id !== "tx") setTenant("");
        }}
        items={[
          { id: "fx", label: "FX cr/kT", value: data.currentExchangeRate.toFixed(1), hint: "Stackelberg", tone: "info" },
          {
            id: "util",
            label: "Utilization",
            value: `${data.systemUtilization}%`,
            hint: data.systemUtilization > 70 ? "High load" : "Nominal",
            tone: data.systemUtilization > 70 ? "danger" : "success",
          },
          { id: "pool", label: "Surplus pool", value: `${(data.surplusPoolSize / 1000).toFixed(1)}k tok`, tone: "neutral" },
          { id: "tx", label: "Ledger rows", value: String(rows.length), hint: tenant || "click a tenant bar", tone: "neutral" },
        ]}
      />

      <Panel title="capacity.load">
        <div className="flex items-center justify-between font-mono text-[11px] text-muted-foreground">
          <span>system.utilization</span>
          <span className="text-foreground">{data.systemUtilization}%</span>
        </div>
        <div className="mt-2 h-2 bg-muted">
          <div
            className={data.systemUtilization > 80 ? "h-full bg-destructive" : "h-full bg-accent"}
            style={{ width: `${Math.min(data.systemUtilization, 100)}%` }}
          />
        </div>
      </Panel>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <Panel title="price.stackelberg" meta="cr / 1k tok · brush zoom">
          <div className="h-[240px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data.priceTrendData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={palette.grid} vertical={false} />
                <XAxis dataKey="time" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <YAxis stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <Tooltip content={<ChartTooltip />} />
                <Line type="stepAfter" dataKey="rate" name="Rate" stroke="#4493f8" strokeWidth={1.5} dot={false} />
                <Brush dataKey="time" height={16} stroke={palette.axis} fill={palette.cursor} travellerWidth={8} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Panel>

        <Panel title="tenant.quota" meta={tenant ? `filter ${tenant}` : "click bar to filter ledger"}>
          <SeriesLegend
            hidden={hidden}
            onToggle={(id) => setHidden((prev) => ({ ...prev, [id]: !prev[id] }))}
            series={[
              { id: "used", label: "Used", color: "#4493f8" },
              { id: "limit", label: "Limit", color: "#656d76" },
            ]}
          />
          <div className="h-[240px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.tenantQuotaData} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={palette.grid} vertical={false} />
                <XAxis dataKey="name" stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} />
                <YAxis stroke={palette.axis} fontSize={11} tickLine={false} axisLine={false} tickFormatter={(val) => `${val / 1000}k`} />
                <Tooltip cursor={{ fill: palette.cursor, opacity: 0.35 }} content={<ChartTooltip />} />
                {!hidden.used ? (
                  <Bar
                    dataKey="used"
                    name="Used"
                    fill="#4493f8"
                    maxBarSize={18}
                    cursor="pointer"
                    onClick={(entry) => {
                      const name = (entry as { name?: string }).name ?? "";
                      setTenant((current) => (current === name ? "" : name));
                    }}
                  />
                ) : null}
                {!hidden.limit ? <Bar dataKey="limit" name="Limit" fill="#656d76" maxBarSize={18} /> : null}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Panel>
      </div>

      <Panel title="ledger.vcg" meta={tenant || "immutable"}>
        <DataTable
          filterPlaceholder="Filter tx id, tenant"
          empty="No borrows in this window"
          rowKey={(row) => row.txId}
          rows={rows}
          copyValue={(row) => `${row.txId}\t${row.borrower}\t${row.lender}\t${row.tokens}\t${row.rate}`}
          renderExpanded={(row) => (
            <p>
              {row.txId}: {row.borrower} borrowed {row.tokens.toLocaleString()} tok from {row.lender} @ {row.rate.toFixed(1)} cr/kT ({row.credits.toFixed(1)} credits)
            </p>
          )}
          columns={[
            { id: "tx", header: "Tx", mono: true, value: (row) => row.txId },
            { id: "borrower", header: "Borrower", value: (row) => row.borrower },
            { id: "lender", header: "Lender", value: (row) => row.lender },
            { id: "tokens", header: "Tokens", align: "right", mono: true, value: (row) => row.tokens.toLocaleString() },
            { id: "rate", header: "Rate", align: "right", mono: true, value: (row) => row.rate.toFixed(1) },
            { id: "credits", header: "Credits", align: "right", mono: true, value: (row) => row.credits.toFixed(1) },
            { id: "time", header: "Time", align: "right", mono: true, value: (row) => row.time },
          ]}
        />
      </Panel>
    </div>
  );
}
