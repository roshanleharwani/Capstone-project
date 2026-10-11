"use client";

import { useCallback, useEffect, useState } from "react";

type Status = "loading" | "ok" | "error";

export function useLivePoll<T>(url: string, initial: T, intervalMs = 5000) {
  const [data, setData] = useState<T>(initial);
  const [status, setStatus] = useState<Status>("loading");
  const [paused, setPaused] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const refresh = useCallback(async () => {
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error("Request failed");
      const json = (await res.json()) as T;
      setData(json);
      setStatus("ok");
      setUpdatedAt(new Date());
    } catch {
      setStatus("error");
    }
  }, [url]);

  useEffect(() => {
    void refresh();
    if (paused) return;
    const id = window.setInterval(() => void refresh(), intervalMs);
    return () => window.clearInterval(id);
  }, [refresh, paused, intervalMs]);

  return { data, status, paused, setPaused, updatedAt, refresh };
}
