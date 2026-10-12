"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { Fragment, useId, useMemo, useState } from "react";

export type Column<T> = {
  id: string;
  header: string;
  align?: "left" | "right";
  mono?: boolean;
  sortable?: boolean;
  className?: string;
  value?: (row: T) => string | number;
  render?: (row: T) => ReactNode;
};

type SortDir = "asc" | "desc";

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  empty = "No rows",
  filterPlaceholder,
  renderExpanded,
  copyValue,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  empty?: string;
  filterPlaceholder?: string;
  renderExpanded?: (row: T) => ReactNode;
  copyValue?: (row: T) => string;
}) {
  const filterId = useId();
  const [query, setQuery] = useState("");
  const [sortId, setSortId] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>("asc");
  const [openKey, setOpenKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const base = !q
      ? rows
      : rows.filter((row) =>
          columns.some((col) => String(col.value ? col.value(row) : "").toLowerCase().includes(q))
        );
    if (!sortId) return base;
    const col = columns.find((item) => item.id === sortId);
    if (!col?.value) return base;
    return [...base].sort((a, b) => {
      const av = col.value!(a);
      const bv = col.value!(b);
      const cmp =
        typeof av === "number" && typeof bv === "number"
          ? av - bv
          : String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: "base" });
      return sortDir === "asc" ? cmp : -cmp;
    });
  }, [columns, query, rows, sortDir, sortId]);

  const toggleSort = (col: Column<T>) => {
    const enabled = col.sortable !== false && col.value;
    if (!enabled) return;
    if (sortId !== col.id) {
      setSortId(col.id);
      setSortDir("asc");
      return;
    }
    if (sortDir === "asc") {
      setSortDir("desc");
      return;
    }
    setSortId(null);
  };

  const toggleRow = (key: string) => {
    setOpenKey((current) => (current === key ? null : key));
  };

  const copyRow = async (row: T) => {
    const text = copyValue ? copyValue(row) : JSON.stringify(row);
    await navigator.clipboard.writeText(text);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div>
      {filterPlaceholder ? (
        <div className="mb-2">
          <label className="sr-only" htmlFor={filterId}>
            {filterPlaceholder}
          </label>
          <input
            id={filterId}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={filterPlaceholder}
            className="h-8 w-full max-w-xs border border-card-border bg-input px-2 font-mono text-xs text-foreground outline-none placeholder:text-muted-foreground focus:border-primary"
          />
        </div>
      ) : null}
      <div className="overflow-x-auto border border-card-border">
        <table className="w-full min-w-[640px] border-collapse text-left text-xs">
          <thead className="sticky top-0 bg-muted">
            <tr>
              {columns.map((col) => {
                const sortable = col.sortable !== false && Boolean(col.value);
                const ariaSort =
                  sortId === col.id ? (sortDir === "asc" ? "ascending" : "descending") : "none";
                return (
                  <th
                    key={col.id}
                    scope="col"
                    aria-sort={sortable ? ariaSort : undefined}
                    className={cn(
                      "border-b border-card-border px-0 font-mono font-medium tracking-wide text-muted-foreground uppercase",
                      col.align === "right" && "text-right",
                      col.className
                    )}
                  >
                    {sortable ? (
                      <button
                        type="button"
                        onClick={() => toggleSort(col)}
                        className={cn(
                          "flex w-full items-center gap-1 px-2 py-1.5 hover:text-foreground",
                          col.align === "right" && "justify-end"
                        )}
                      >
                        {col.header}
                        <span aria-hidden="true" className="font-mono text-[10px]">
                          {sortId === col.id ? (sortDir === "asc" ? "^" : "v") : "·"}
                        </span>
                      </button>
                    ) : (
                      <span className="block px-2 py-1.5">{col.header}</span>
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className="px-2 py-6 text-center text-muted-foreground">
                  {empty}
                </td>
              </tr>
            ) : (
              filtered.map((row) => {
                const key = rowKey(row);
                const open = openKey === key;
                return (
                  <Fragment key={key}>
                    <tr
                      tabIndex={0}
                      aria-selected={open}
                      aria-expanded={renderExpanded ? open : undefined}
                      onClick={() => toggleRow(key)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          toggleRow(key);
                        }
                      }}
                      className={cn(
                        "cursor-pointer border-b border-card-border last:border-b-0 hover:bg-overlay",
                        open && "bg-overlay"
                      )}
                    >
                      {columns.map((col) => (
                        <td
                          key={col.id}
                          className={cn(
                            "px-2 py-1.5 text-foreground",
                            col.mono && "font-mono tabular-nums",
                            col.align === "right" && "text-right",
                            col.className
                          )}
                        >
                          {col.render ? col.render(row) : col.value ? col.value(row) : null}
                        </td>
                      ))}
                    </tr>
                    {open && renderExpanded ? (
                      <tr className="border-b border-card-border bg-muted/40">
                        <td colSpan={columns.length} className="px-3 py-2">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0 flex-1 font-mono text-[11px] text-muted-foreground">
                              {renderExpanded(row)}
                            </div>
                            <button
                              type="button"
                              onClick={(event) => {
                                event.stopPropagation();
                                void copyRow(row);
                              }}
                              className="shrink-0 font-mono text-[11px] text-muted-foreground hover:text-foreground"
                            >
                              {copied ? "Copied" : "Copy"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      <p className="mt-1.5 font-mono text-[11px] text-muted-foreground">
        {filtered.length} / {rows.length} rows
        {sortId ? ` · sort ${sortId} ${sortDir}` : ""}
        {openKey ? " · row open · Enter to close" : " · click row to inspect"}
      </p>
    </div>
  );
}
