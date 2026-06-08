import { useEffect } from "react";
import { Users } from "lucide-react";

import { initialsFor } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ChatRow } from "@/types/waview";

interface ChatAvatarProps {
  chat: Pick<ChatRow, "id" | "title" | "isGroup" | "avatarFile">;
  avatarUrl?: string;
  avatarError?: string;
  onLoadAvatar?: (chatId: number) => void;
  size?: "sm" | "md" | "lg";
}

export function ChatAvatar({ chat, avatarUrl, avatarError, onLoadAvatar, size = "md" }: ChatAvatarProps) {
  useEffect(() => {
    if (chat.avatarFile && !avatarUrl && !avatarError) onLoadAvatar?.(chat.id);
  }, [avatarError, avatarUrl, chat.avatarFile, chat.id, onLoadAvatar]);

  const sizeClass = size === "lg" ? "h-20 w-20 text-xl" : size === "sm" ? "h-9 w-9 text-xs" : "h-11 w-11 text-sm";

  return (
    <div
      className={cn(
        "relative grid shrink-0 place-items-center overflow-hidden rounded-full bg-primary/10 font-semibold text-primary",
        sizeClass,
      )}
    >
      {avatarUrl ? <img src={avatarUrl} alt="" className="h-full w-full object-cover" /> : chat.isGroup ? <Users className="h-4 w-4" /> : initialsFor(chat.title)}
      <span className="pointer-events-none absolute inset-0 rounded-full ring-1 ring-black/5" />
    </div>
  );
}
