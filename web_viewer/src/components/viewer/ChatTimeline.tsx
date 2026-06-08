import { useEffect, useRef } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";

import { ChatAvatar } from "@/components/viewer/ChatAvatar";
import { EmptyState } from "@/components/viewer/EmptyState";
import { MessageBubble } from "@/components/viewer/MessageBubble";
import type { MediaUrlRecord } from "@/hooks/useWaviewArchive";
import { formatTimestamp } from "@/lib/format";
import type { ChatRow, ViewerMessage } from "@/types/waview";

interface ChatTimelineProps {
  chat: ChatRow | null;
  messages: ViewerMessage[];
  avatarUrl?: string;
  avatarError?: string;
  mediaUrls: Record<number, MediaUrlRecord>;
  mediaErrors: Record<number, string>;
  onLoadAvatar: (chatId: number) => void;
  onEnsureMedia: (messageId: number) => void;
  onOpenMedia: (message: ViewerMessage) => void;
}

function dayKey(timestamp: number) {
  if (!timestamp) return "";
  return new Date(timestamp).toDateString();
}

export function ChatTimeline({
  chat,
  messages,
  avatarUrl,
  avatarError,
  mediaUrls,
  mediaErrors,
  onLoadAvatar,
  onEnsureMedia,
  onOpenMedia,
}: ChatTimelineProps) {
  const parentRef = useRef<HTMLDivElement | null>(null);
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 86,
    overscan: 10,
  });

  useEffect(() => {
    if (messages.length) {
      requestAnimationFrame(() => virtualizer.scrollToIndex(messages.length - 1, { align: "end" }));
    }
  }, [messages.length, virtualizer]);

  if (!chat) {
    return <EmptyState title="Select a chat" body="Choose a conversation from the left sidebar to inspect messages and media." />;
  }

  return (
    <section className="glass-card flex h-full min-h-0 flex-col overflow-hidden rounded-2xl">
      <header className="flex h-[68px] shrink-0 items-center justify-between border-b border-border px-4">
        <div className="flex min-w-0 items-center gap-3">
          <ChatAvatar chat={chat} avatarUrl={avatarUrl} avatarError={avatarError} onLoadAvatar={onLoadAvatar} />
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold text-foreground">{chat.title}</div>
            <div className="truncate text-xs text-muted-foreground">
              {chat.isGroup ? `${chat.memberCount || "Group"} members` : `${chat.messageCount.toLocaleString()} messages`}
            </div>
          </div>
        </div>
        <div className="hidden rounded-xl bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary md:block">
          {chat.mediaCount.toLocaleString()} media
        </div>
      </header>

      <div ref={parentRef} className="chat-wallpaper thin-scrollbar min-h-0 flex-1 overflow-auto">
        {!messages.length ? (
          <EmptyState title="Loading messages" body="The worker is preparing this conversation." />
        ) : (
          <div className="relative w-full py-4" style={{ height: `${virtualizer.getTotalSize() + 32}px` }}>
            {virtualizer.getVirtualItems().map((virtualRow) => {
              const message = messages[virtualRow.index];
              const previous = messages[virtualRow.index - 1];
              const showDate = !previous || dayKey(previous.timestamp) !== dayKey(message.timestamp);
              return (
                <div
                  key={message.id}
                  ref={virtualizer.measureElement}
                  data-index={virtualRow.index}
                  className="absolute left-0 top-0 w-full"
                  style={{ transform: `translateY(${virtualRow.start}px)` }}
                >
                  {showDate ? (
                    <div className="mb-3 flex justify-center">
                      <div className="rounded-md bg-card/80 px-3 py-1 text-xs font-medium text-muted-foreground shadow-sm backdrop-blur">
                        {formatTimestamp(message.timestamp, "Unknown date").split(",")[0]}
                      </div>
                    </div>
                  ) : null}
                  <MessageBubble
                    message={message}
                    isGroup={chat.isGroup}
                    mediaUrl={mediaUrls[message.id]}
                    mediaError={mediaErrors[message.id]}
                    onEnsureMedia={onEnsureMedia}
                    onOpenMedia={onOpenMedia}
                  />
                </div>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
}
