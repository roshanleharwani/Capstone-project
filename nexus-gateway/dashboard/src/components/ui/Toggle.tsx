import { cn } from "@/lib/utils";

type ToggleProps = {
  checked: boolean;
  onChange: () => void;
  label: string;
  description?: string;
};

export function Toggle({ checked, onChange, label }: ToggleProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={onChange}
      className={cn(
        "relative h-5 w-9 border border-card-border transition-colors duration-150",
        checked ? "bg-foreground" : "bg-muted"
      )}
    >
      <span
        className={cn(
          "absolute top-0.5 h-4 w-4 bg-background transition-transform duration-150",
          checked ? "translate-x-4" : "translate-x-0.5"
        )}
      />
    </button>
  );
}
