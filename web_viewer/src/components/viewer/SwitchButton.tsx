import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

interface SwitchButtonProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  icon?: LucideIcon;
  label?: string;
}

export function SwitchButton({ checked, onCheckedChange, icon: Icon, label }: SwitchButtonProps) {
  return (
    <button
      type="button"
      onClick={() => onCheckedChange(!checked)}
      className={cn(
        "inline-flex h-10 items-center gap-2 rounded-xl border border-border bg-background/70 px-3 text-sm font-medium shadow-sm backdrop-blur transition-all hover:bg-accent",
        checked && "border-primary/40 bg-primary/10 text-primary",
      )}
    >
      {Icon ? <Icon className="h-4 w-4" /> : null}
      {label ? <span>{label}</span> : null}
      <span className={cn("relative h-5 w-9 rounded-full transition-colors", checked ? "bg-primary" : "bg-muted-foreground/30")}>
        <span
          className={cn(
            "absolute top-0.5 h-4 w-4 rounded-full bg-background shadow transition-transform",
            checked ? "translate-x-4" : "translate-x-0.5",
          )}
        />
      </span>
    </button>
  );
}
