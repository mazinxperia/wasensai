import { useEffect } from "react";
import { Download, EyeOff, FileText, Forward, Image, MapPin, Mic, Play, Star, Sticker, Video } from "lucide-react";

import type { MediaUrlRecord } from "@/hooks/useWaviewArchive";
import { formatBytes, formatDuration, formatMessageTime, isPreviewableImage, messageTypeLabel, mimeMajor } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ViewerMessage } from "@/types/waview";

interface MessageBubbleProps {
  message: ViewerMessage;
  isGroup: boolean;
  mediaUrl?: MediaUrlRecord;
  mediaError?: string;
  onEnsureMedia: (messageId: number) => void;
  onOpenMedia: (message: ViewerMessage) => void;
}

function MediaKindIcon({ type, mime }: { type: number; mime?: string }) {
  if (type === 1 || type === 13 || mime?.startsWith("image/")) return <Image className="h-5 w-5" />;
  if (type === 3 || mime?.startsWith("video/")) return <Video className="h-5 w-5" />;
  if (type === 2 || mime?.startsWith("audio/")) return <Mic className="h-5 w-5" />;
  if (type === 20) return <Sticker className="h-5 w-5" />;
  return <FileText className="h-5 w-5" />;
}

export function MessageBubble({ message, isGroup, mediaUrl, mediaError, onEnsureMedia, onOpenMedia }: MessageBubbleProps) {
  const hasMedia = Boolean(message.media);
  const mime = mediaUrl?.mimeType || message.media?.mime_type || "";
  const major = mimeMajor(mime);
  const fileName = mediaUrl?.fileName || message.media?.file_name || messageTypeLabel(message.messageType);
  const mediaStatus = mediaError
    ? "unavailable"
    : message.media?.status === "not_embedded"
      ? "not embedded"
      : message.media?.status || "not embedded";
  const shouldInlineLoad =
    hasMedia &&
    message.media?.status === "downloaded" &&
    !mediaUrl &&
    !mediaError &&
    (major === "image" ||
      major === "audio" ||
      message.messageType === 1 ||
      message.messageType === 2 ||
      message.messageType === 13 ||
      message.messageType === 20);

  useEffect(() => {
    if (shouldInlineLoad) onEnsureMedia(message.id);
  }, [message.id, onEnsureMedia, shouldInlineLoad]);

  if (message.isSystem || message.messageType === 7) {
    return (
      <div className="my-3 flex justify-center">
        <div className="max-w-[78%] rounded-md bg-card/80 px-3 py-1.5 text-center text-xs text-muted-foreground shadow-sm backdrop-blur">
          {message.text || messageTypeLabel(message.messageType)}
        </div>
      </div>
    );
  }

  const incoming = !message.fromMe;
  const showInlineImage = Boolean(mediaUrl && major === "image" && isPreviewableImage(mime, fileName));
  const showInlineVideo = Boolean(mediaUrl && major === "video");
  const showInlineAudio = Boolean(mediaUrl && major === "audio");
  const displayText =
    message.deletedForEveryone || message.isDeleted || message.messageType === 15
      ? "This message was deleted"
      : message.text || message.media?.caption || "";

  if (message.messageType === 84 || message.messageType === 85) {
    return (
      <div className={cn("flex w-full px-4 py-1.5", incoming ? "justify-start" : "justify-end")}>
        <div
          className={cn(
            "flex max-w-[74%] items-center gap-2 rounded-lg px-3 py-2 text-sm shadow-sm md:max-w-[62%]",
            incoming ? "rounded-tl-sm bg-card text-foreground" : "rounded-tr-sm bg-primary/15 text-foreground",
          )}
        >
          <EyeOff className="h-4 w-4 text-muted-foreground" />
          <div className="min-w-0">
            <div className="text-sm text-muted-foreground">
              {message.messageType === 85 ? "View once video expired" : "View once photo expired"}
            </div>
            <div className="mt-1 text-right text-[10px] text-muted-foreground">{formatMessageTime(message.timestamp)}</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={cn("flex w-full px-4 py-1.5", incoming ? "justify-start" : "justify-end")}>
      <div
        className={cn(
          "group relative max-w-[74%] rounded-lg px-3 py-2 text-sm shadow-sm md:max-w-[62%]",
          incoming ? "rounded-tl-sm bg-card text-foreground" : "rounded-tr-sm bg-primary/15 text-foreground",
        )}
      >
        {incoming && isGroup && message.senderName ? (
          <div className="mb-1 text-xs font-semibold text-primary">{message.senderName}</div>
        ) : null}

        {message.quotedText ? (
          <div className="mb-2 border-l-4 border-primary/60 bg-black/5 px-2 py-1 text-xs text-muted-foreground dark:bg-white/5">
            <div className="font-medium text-primary">{message.quotedSenderName || "Quoted message"}</div>
            <div className="line-clamp-2">{message.quotedText}</div>
          </div>
        ) : null}

        {message.isForwarded ? (
          <div className="mb-1 flex items-center gap-1 text-[11px] italic text-muted-foreground">
            <Forward className="h-3 w-3" />
            Forwarded
          </div>
        ) : null}

        {hasMedia ? (
          <div className="mb-2">
            {showInlineImage ? (
              <button type="button" onClick={() => onOpenMedia(message)} className="block overflow-hidden rounded-md bg-black/5 dark:bg-white/5">
                <img src={mediaUrl?.url} alt="" className="max-h-[320px] max-w-full object-contain" />
              </button>
            ) : showInlineVideo ? (
              <video src={mediaUrl?.url} controls className="max-h-[320px] max-w-full rounded-md bg-black" />
            ) : showInlineAudio ? (
              <div className="min-w-[240px] rounded-md bg-background/60 p-2">
                <audio src={mediaUrl?.url} controls className="w-full" />
              </div>
            ) : (
              <button
                type="button"
                onClick={() => onOpenMedia(message)}
                className="flex w-full min-w-[220px] items-center gap-3 rounded-md border border-border bg-background/55 p-2 text-left transition hover:bg-background/80"
              >
                <div className="grid h-12 w-12 shrink-0 place-items-center rounded-md bg-primary text-primary-foreground">
                  <MediaKindIcon type={message.messageType} mime={message.media?.mime_type} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-semibold text-foreground">{fileName}</div>
                  <div className="mt-0.5 flex flex-wrap gap-2 text-xs text-muted-foreground">
                    {message.media?.size ? <span>{formatBytes(message.media.size)}</span> : null}
                    {message.media?.duration ? <span>{formatDuration(message.media.duration)}</span> : null}
                    <span>{mediaStatus}</span>
                  </div>
                  {mediaError ? <div className="mt-1 line-clamp-2 text-xs text-destructive">{mediaError}</div> : null}
                </div>
                {message.media?.status === "downloaded" ? (
                  message.messageType === 3 || message.media?.mime_type?.startsWith("video/") ? (
                    <Play className="h-4 w-4 text-muted-foreground" />
                  ) : (
                    <Download className="h-4 w-4 text-muted-foreground" />
                  )
                ) : null}
              </button>
            )}
          </div>
        ) : null}

        {message.messageType === 5 ? (
          <div className="mb-2 flex items-start gap-2 rounded-md bg-background/60 p-2">
            <MapPin className="mt-0.5 h-4 w-4 text-primary" />
            <div>
              <div className="text-sm font-medium">{message.placeName || "Location"}</div>
              <div className="text-xs text-muted-foreground">
                {message.placeAddress || `${message.latitude}, ${message.longitude}`}
              </div>
            </div>
          </div>
        ) : null}

        {message.poll ? (
          <div className="mb-2 rounded-md bg-background/60 p-2">
            <div className="text-sm font-semibold">{message.poll.question || "Poll"}</div>
            <div className="mt-2 space-y-1">
              {(message.poll.options || []).map((option) => (
                <div key={option.id || option.option_name} className="rounded bg-muted px-2 py-1 text-xs text-muted-foreground">
                  {option.option_name || "Option"} · {option.vote_total || 0}
                </div>
              ))}
            </div>
          </div>
        ) : null}

        {displayText ? (
          <p className={cn("whitespace-pre-wrap break-words leading-5", message.isDeleted && "italic text-muted-foreground")}>
            {displayText}
          </p>
        ) : !hasMedia ? (
          <p className="text-muted-foreground">{messageTypeLabel(message.messageType)}</p>
        ) : null}

        {message.reactions.length ? (
          <div className="mt-2 flex flex-wrap gap-1">
            {message.reactions.map((reaction, index) => (
              <span key={`${reaction.emoji}-${index}`} className="rounded-full bg-background px-1.5 py-0.5 text-xs shadow-sm">
                {reaction.emoji}
              </span>
            ))}
          </div>
        ) : null}

        <div className="mt-1 flex items-center justify-end gap-1 text-[10px] text-muted-foreground">
          {message.starred ? <Star className="h-3 w-3 fill-amber-400 text-amber-400" /> : null}
          <span>{formatMessageTime(message.timestamp)}</span>
        </div>
      </div>
    </div>
  );
}
