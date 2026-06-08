import { Phone, PhoneIncoming, PhoneOutgoing, Video } from "lucide-react";

import { EmptyState } from "@/components/viewer/EmptyState";
import { formatDuration, formatTimestamp } from "@/lib/format";
import type { ViewerCall } from "@/types/waview";

interface CallsViewProps {
  calls: ViewerCall[];
}

export function CallsView({ calls }: CallsViewProps) {
  if (!calls.length) {
    return <EmptyState title="No calls exported" body="This archive does not include call records." />;
  }

  return (
    <section className="glass-card flex h-full min-h-0 flex-col overflow-hidden rounded-2xl">
      <header className="flex h-[68px] shrink-0 items-center border-b border-border px-5">
        <div className="grid h-10 w-10 place-items-center rounded-xl bg-primary text-primary-foreground">
          <Phone className="h-5 w-5" />
        </div>
        <div className="ml-3">
          <div className="font-heading text-sm font-semibold text-foreground">Calls</div>
          <div className="text-xs text-muted-foreground">{calls.length.toLocaleString()} records</div>
        </div>
      </header>
      <div className="thin-scrollbar min-h-0 flex-1 overflow-auto">
        {calls.map((call) => {
          const Direction = call.fromMe ? PhoneOutgoing : PhoneIncoming;
          return (
            <div key={`${call.id}-${call.timestamp}`} className="flex items-center gap-3 border-b border-border/60 px-5 py-3 transition-colors hover:bg-accent/50">
              <div className="grid h-11 w-11 place-items-center rounded-full bg-primary/10 text-primary">
                {call.isVideo ? <Video className="h-5 w-5" /> : <Phone className="h-5 w-5" />}
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold text-foreground">{call.title || call.jid}</div>
                <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                  <Direction className="h-3.5 w-3.5" />
                  <span>{formatTimestamp(call.timestamp, "Unknown date")}</span>
                  {call.duration ? <span>{formatDuration(call.duration)}</span> : null}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
