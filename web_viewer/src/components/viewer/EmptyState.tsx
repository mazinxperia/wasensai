import { Archive, MessageCircle } from "lucide-react";

interface EmptyStateProps {
  title: string;
  body: string;
  mode?: "chat" | "archive";
}

export function EmptyState({ title, body, mode = "chat" }: EmptyStateProps) {
  const Icon = mode === "archive" ? Archive : MessageCircle;
  return (
    <div className="flex h-full min-h-[360px] flex-col items-center justify-center px-6 text-center">
      <div className="grid h-16 w-16 place-items-center rounded-2xl bg-primary/10 text-primary shadow-panel">
        <Icon className="h-7 w-7" />
      </div>
      <h2 className="mt-5 font-heading text-xl font-semibold tracking-tight text-foreground">{title}</h2>
      <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">{body}</p>
    </div>
  );
}
