type TooltipPayload = {
  name?: string;
  value?: number | string;
  color?: string;
};

export function ChartTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: TooltipPayload[];
  label?: string;
}) {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-lg border border-card-border bg-card px-3 py-2 text-xs shadow-lg">
      <p className="mb-1 font-semibold text-foreground">{label}</p>
      {payload.map((item) => (
        <p key={item.name} className="flex items-center gap-2 text-muted-foreground">
          <span className="h-2 w-2 rounded-full" style={{ background: item.color }} />
          {item.name}: <span className="font-mono text-foreground">{item.value}</span>
        </p>
      ))}
    </div>
  );
}
