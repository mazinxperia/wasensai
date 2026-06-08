import { useEffect, useMemo, useRef, useState } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Search, Star } from "lucide-react";

import { ChatAvatar } from "@/components/viewer/ChatAvatar";
import { formatChatDate, messageTypeLabel } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ChatFilter } from "@/types/worker";
import type { ArchiveSummary, ChatRow } from "@/types/waview";

interface ChatSidebarProps {
  summary: ArchiveSummary;
  chats: ChatRow[];
  activeChatId: number | null;
  filter: ChatFilter;
  avatarUrls: Record<number, string>;
  avatarErrors: Record<number, string>;
  onFilterChange: (filter: ChatFilter) => void;
  onSelectChat: (chatId: number) => void;
  onLoadAvatar: (chatId: number) => void;
  onCloseArchive: () => void;
}

function filterChats(chats: ChatRow[], query: string, filter: ChatFilter) {
  const normalizedQuery = query.trim().toLowerCase();
  return chats.filter((chat) => {
    if (filter === "groups" && !chat.isGroup) return false;
    if (filter === "media" && !chat.mediaCount) return false;
    if (filter === "starred" && !chat.starredCount) return false;
    if (filter === "calls") return false;
    if (!normalizedQuery) return true;
    return (
      chat.title.toLowerCase().includes(normalizedQuery) ||
      chat.subtitle.toLowerCase().includes(normalizedQuery) ||
      chat.jid.toLowerCase().includes(normalizedQuery)
    );
  });
}

export function ChatSidebar({
  summary,
  chats,
  activeChatId,
  filter,
  avatarUrls,
  avatarErrors,
  onSelectChat,
  onLoadAvatar,
}: ChatSidebarProps) {
  const [query, setQuery] = useState("");
  const parentRef = useRef<HTMLDivElement | null>(null);
  const filteredChats = useMemo(() => filterChats(chats, query, filter), [chats, filter, query]);
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({
    count: filteredChats.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 76,
    overscan: 12,
  });

  useEffect(() => {
    parentRef.current?.scrollTo({ top: 0 });
  }, [filter, query, summary.fileName]);

  return (
    <aside className="glass-card flex h-full min-h-0 w-full flex-col overflow-hidden rounded-2xl">
      <div className="border-b border-border px-4 py-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="font-heading text-lg font-semibold">Conversations</div>
            <div className="mt-1 truncate text-xs text-muted-foreground">{summary.fileName}</div>
          </div>
          <div className="rounded-xl bg-primary/10 px-3 py-1 text-sm font-semibold text-primary">
            {filteredChats.length.toLocaleString()}
          </div>
        </div>

        <div className="mt-4 grid grid-cols-3 gap-2 text-center">
          <div className="rounded-xl bg-secondary/60 px-2 py-2">
            <div className="text-sm font-semibold text-foreground">{summary.counts.chats.toLocaleString()}</div>
            <div className="text-[10px] uppercase tracking-[0.12em] text-muted-foreground">Chats</div>
          </div>
          <div className="rounded-xl bg-secondary/60 px-2 py-2">
            <div className="text-sm font-semibold text-foreground">{summary.counts.messages.toLocaleString()}</div>
            <div className="text-[10px] uppercase tracking-[0.12em] text-muted-foreground">Messages</div>
          </div>
          <div className="rounded-xl bg-secondary/60 px-2 py-2">
            <div className="text-sm font-semibold text-foreground">{summary.counts.media.toLocaleString()}</div>
            <div className="text-[10px] uppercase tracking-[0.12em] text-muted-foreground">Media</div>
          </div>
        </div>
      </div>

      <div className="border-b border-border px-3 py-3">
        <label className="relative block">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search chats"
            className="h-10 w-full rounded-xl border border-transparent bg-secondary/60 pl-9 pr-3 text-sm outline-none transition focus:border-primary/40 focus:bg-background focus:ring-2 focus:ring-primary/15"
          />
        </label>
      </div>

      {filter === "calls" ? (
        null
      ) : (
        <div ref={parentRef} className="thin-scrollbar min-h-0 flex-1 overflow-auto">
          <div className="relative w-full" style={{ height: `${virtualizer.getTotalSize()}px` }}>
            {virtualizer.getVirtualItems().map((virtualRow) => {
              const chat = filteredChats[virtualRow.index];
              const isActive = chat.id === activeChatId;
              return (
                <button
                  key={chat.id}
                  type="button"
                  onClick={() => onSelectChat(chat.id)}
                  className={cn(
                    "absolute left-0 top-0 flex w-full items-center gap-3 border-b border-border/60 px-4 py-3 text-left transition",
                    isActive ? "bg-primary/10" : "hover:bg-accent/60",
                  )}
                  style={{ height: `${virtualRow.size}px`, transform: `translateY(${virtualRow.start}px)` }}
                >
                  <ChatAvatar
                    chat={chat}
                    avatarUrl={avatarUrls[chat.id]}
                    avatarError={avatarErrors[chat.id]}
                    onLoadAvatar={onLoadAvatar}
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <div className="truncate text-sm font-semibold text-foreground">{chat.title}</div>
                      <div className="shrink-0 text-[11px] text-muted-foreground">{formatChatDate(chat.timestamp)}</div>
                    </div>
                    <div className="mt-1 flex items-center gap-2">
                      <div className="truncate text-sm text-muted-foreground">
                        {chat.subtitle || messageTypeLabel(chat.lastMessageType)}
                      </div>
                      {chat.mediaCount ? <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-medium text-primary">{chat.mediaCount}</span> : null}
                      {chat.starredCount ? <Star className="h-3 w-3 fill-amber-400 text-amber-400" /> : null}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
          {!filteredChats.length ? (
            <div className="flex h-full items-center justify-center px-8 text-center text-sm text-muted-foreground">
              No chats match this filter.
            </div>
          ) : null}
        </div>
      )}
    </aside>
  );
}
