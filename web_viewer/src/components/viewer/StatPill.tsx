import type { LucideIcon } from "lucide-react";

interface StatPillProps {
  icon: LucideIcon;
  label: string;
  value: string | number;
}

export function StatPill({ icon: Icon, label, value }: StatPillProps) {
  return (
    <div className="flex items-center gap-2 rounded-xl border border-border bg-background/70 px-3 py-2 shadow-sm">
      <div className="grid h-8 w-8 place-items-center rounded-lg bg-primary/10 text-primary">
        <Icon className="h-4 w-4" />
      </div>
      <div>
        <div className="text-sm font-semibold text-foreground">{value}</div>
        <div className="text-[11px] font-medium uppercase tracking-[0.12em] text-muted-foreground">{label}</div>
      </div>
    </div>
  );
}
