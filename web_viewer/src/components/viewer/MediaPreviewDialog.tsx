import { useEffect } from "react";
import { Download, FileText, ImageOff, Loader2 } from "lucide-react";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { formatBytes, isPreviewableImage, mimeMajor } from "@/lib/format";
import type { MediaUrlRecord } from "@/hooks/useWaviewArchive";
import type { ViewerMessage } from "@/types/waview";

interface MediaPreviewDialogProps {
  message: ViewerMessage | null;
  media?: MediaUrlRecord;
  mediaError?: string;
  onOpenChange: (open: boolean) => void;
  onLoadMedia: (messageId: number) => void;
}

export function MediaPreviewDialog({ message, media, mediaError, onOpenChange, onLoadMedia }: MediaPreviewDialogProps) {
  const open = Boolean(message);
  const mediaEntry = message?.media;

  useEffect(() => {
    if (message?.id && mediaEntry?.status === "downloaded" && !media) onLoadMedia(message.id);
  }, [media, mediaEntry?.status, message?.id, onLoadMedia]);

  const mime = media?.mimeType || mediaEntry?.mime_type || "";
  const major = mimeMajor(mime);
  const fileName = media?.fileName || mediaEntry?.file_name || "media";
  const unavailable = Boolean(mediaError) || (mediaEntry && mediaEntry.status !== "downloaded");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="overflow-hidden bg-slate-950 text-white">
        <DialogHeader className="border-b border-white/10 px-5 py-4">
          <DialogTitle className="truncate pr-12 text-base">{fileName}</DialogTitle>
          <DialogDescription className="text-slate-400">
            {mime || "unknown"} {mediaEntry?.size ? `· ${formatBytes(mediaEntry.size)}` : ""}
          </DialogDescription>
        </DialogHeader>

        <div className="flex min-h-[420px] items-center justify-center bg-black">
          {unavailable ? (
            <div className="flex max-w-sm flex-col items-center px-6 text-center">
              <ImageOff className="h-10 w-10 text-slate-500" />
              <div className="mt-4 text-sm font-medium">Media is not available</div>
              <p className="mt-2 text-sm text-slate-400">
                {mediaError || "`data.json` references this file, but the bytes are not present in this `.waview` archive."}
              </p>
            </div>
          ) : !media ? (
            <div className="flex flex-col items-center gap-3 text-slate-300">
              <Loader2 className="h-8 w-8 animate-spin" />
              <div className="text-sm">Loading media slice from local archive</div>
            </div>
          ) : major === "image" && isPreviewableImage(mime, fileName) ? (
            <img src={media.url} alt="" className="max-h-[72vh] max-w-full object-contain" />
          ) : major === "video" ? (
            <video src={media.url} controls className="max-h-[72vh] max-w-full" />
          ) : major === "audio" ? (
            <div className="w-full max-w-xl px-8">
              <audio src={media.url} controls className="w-full" />
            </div>
          ) : (
            <div className="flex max-w-sm flex-col items-center px-6 text-center">
              <div className="grid h-16 w-16 place-items-center rounded-lg bg-white/10">
                <FileText className="h-8 w-8" />
              </div>
              <div className="mt-4 text-sm font-medium">{fileName}</div>
              <div className="mt-1 text-xs text-slate-400">{formatBytes(media.size)}</div>
              <Button asChild className="mt-5" variant="glass">
                <a href={media.url} download={fileName}>
                  <Download className="h-4 w-4" />
                  Download / open
                </a>
              </Button>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
