import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import type { ArchiveSummary, ChatRow, MediaBlobResult, ViewerCall, ViewerMessage } from "@/types/waview";
import type { WorkerProgress, WorkerRequest, WorkerResponse } from "@/types/worker";

type Status = "idle" | "loading" | "ready" | "error";

interface ObjectUrlRecord {
  url: string;
  mimeType: string;
  fileName: string;
  size: number;
}

const idleProgress: WorkerProgress = {
  type: "PROGRESS",
  phase: "Idle",
  detail: "Select a .waview file to begin",
  progress: 0,
};

export function useWaviewArchive() {
  const workerRef = useRef<Worker | null>(null);
  const mediaPendingRef = useRef(new Set<number>());
  const avatarPendingRef = useRef(new Set<number>());
  const objectUrlsRef = useRef<string[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [progress, setProgress] = useState<WorkerProgress>(idleProgress);
  const [error, setError] = useState("");
  const [summary, setSummary] = useState<ArchiveSummary | null>(null);
  const [chats, setChats] = useState<ChatRow[]>([]);
  const [calls, setCalls] = useState<ViewerCall[]>([]);
  const [activeChatId, setActiveChatId] = useState<number | null>(null);
  const [messagesByChatId, setMessagesByChatId] = useState<Record<number, ViewerMessage[]>>({});
  const [mediaUrls, setMediaUrls] = useState<Record<number, ObjectUrlRecord>>({});
  const [mediaErrors, setMediaErrors] = useState<Record<number, string>>({});
  const [avatarUrls, setAvatarUrls] = useState<Record<number, string>>({});
  const [avatarErrors, setAvatarErrors] = useState<Record<number, string>>({});

  const revokeAllUrls = useCallback(() => {
    for (const url of objectUrlsRef.current) URL.revokeObjectURL(url);
    objectUrlsRef.current = [];
  }, []);

  const ensureWorker = useCallback(() => {
    if (workerRef.current) return workerRef.current;
    const worker = new Worker(new URL("../workers/waview.worker.ts", import.meta.url), { type: "module" });

    worker.onmessage = (event: MessageEvent<WorkerResponse>) => {
      const message = event.data;

      if (message.type === "PROGRESS") {
        setProgress(message);
        setStatus("loading");
      }

      if (message.type === "ARCHIVE_READY") {
        setSummary(message.summary);
        setChats(message.chats);
        setCalls(message.calls);
        setError("");
        setStatus("ready");
        setProgress({ type: "PROGRESS", phase: "Ready", detail: "Archive loaded", progress: 100 });
      }

      if (message.type === "MESSAGES_READY") {
        setMessagesByChatId((current) => ({
          ...current,
          [message.chatId]: message.messages,
        }));
      }

      if (message.type === "MEDIA_READY") {
        mediaPendingRef.current.delete(message.media.messageId);
        setMediaErrors((current) => {
          const next = { ...current };
          delete next[message.media.messageId];
          return next;
        });
        setMediaUrls((current) => {
          const previous = current[message.media.messageId];
          if (previous) URL.revokeObjectURL(previous.url);
          const url = URL.createObjectURL(message.media.blob);
          objectUrlsRef.current.push(url);
          return {
            ...current,
            [message.media.messageId]: {
              url,
              mimeType: message.media.mimeType,
              fileName: message.media.fileName,
              size: message.media.size,
            },
          };
        });
      }

      if (message.type === "MEDIA_ERROR") {
        mediaPendingRef.current.delete(message.messageId);
        setMediaErrors((current) => ({ ...current, [message.messageId]: message.message }));
      }

      if (message.type === "AVATAR_READY") {
        avatarPendingRef.current.delete(message.avatar.chatId);
        setAvatarErrors((current) => {
          const next = { ...current };
          delete next[message.avatar.chatId];
          return next;
        });
        setAvatarUrls((current) => {
          const previous = current[message.avatar.chatId];
          if (previous) URL.revokeObjectURL(previous);
          const url = URL.createObjectURL(message.avatar.blob);
          objectUrlsRef.current.push(url);
          return { ...current, [message.avatar.chatId]: url };
        });
      }

      if (message.type === "AVATAR_ERROR") {
        avatarPendingRef.current.delete(message.chatId);
        setAvatarErrors((current) => ({ ...current, [message.chatId]: message.message }));
      }

      if (message.type === "ERROR") {
        mediaPendingRef.current.clear();
        avatarPendingRef.current.clear();
        setError(message.message);
        setStatus((current) => (current === "ready" ? "ready" : "error"));
      }

      if (message.type === "CLOSED") {
        setStatus("idle");
      }
    };

    workerRef.current = worker;
    return worker;
  }, []);

  const post = useCallback(
    (request: WorkerRequest) => {
      ensureWorker().postMessage(request);
    },
    [ensureWorker],
  );

  const resetState = useCallback(() => {
    revokeAllUrls();
    mediaPendingRef.current.clear();
    avatarPendingRef.current.clear();
    setSummary(null);
    setChats([]);
    setCalls([]);
    setActiveChatId(null);
    setMessagesByChatId({});
    setMediaUrls({});
    setMediaErrors({});
    setAvatarUrls({});
    setAvatarErrors({});
    setError("");
  }, [revokeAllUrls]);

  const openArchive = useCallback(
    (file: File) => {
      resetState();
      setStatus("loading");
      setProgress({ type: "PROGRESS", phase: "Opening", detail: file.name, progress: 3 });
      post({ type: "OPEN_ARCHIVE", file });
    },
    [post, resetState],
  );

  const closeArchive = useCallback(() => {
    post({ type: "CLOSE_ARCHIVE" });
    resetState();
    setProgress(idleProgress);
    setStatus("idle");
  }, [post, resetState]);

  const selectChat = useCallback(
    (chatId: number) => {
      setActiveChatId(chatId);
      if (!messagesByChatId[chatId]) post({ type: "GET_MESSAGES", chatId });
    },
    [messagesByChatId, post],
  );

  const loadMedia = useCallback(
    (messageId: number) => {
      if (mediaUrls[messageId] || mediaPendingRef.current.has(messageId)) return;
      setMediaErrors((current) => {
        const next = { ...current };
        delete next[messageId];
        return next;
      });
      mediaPendingRef.current.add(messageId);
      post({ type: "GET_MEDIA", messageId });
    },
    [mediaUrls, post],
  );

  const loadAvatar = useCallback(
    (chatId: number) => {
      if (avatarUrls[chatId] || avatarPendingRef.current.has(chatId)) return;
      setAvatarErrors((current) => {
        const next = { ...current };
        delete next[chatId];
        return next;
      });
      avatarPendingRef.current.add(chatId);
      post({ type: "GET_AVATAR", chatId });
    },
    [avatarUrls, post],
  );

  useEffect(() => {
    return () => {
      revokeAllUrls();
      workerRef.current?.terminate();
      workerRef.current = null;
    };
  }, [revokeAllUrls]);

  const activeChat = useMemo(() => chats.find((chat) => chat.id === activeChatId) || null, [activeChatId, chats]);
  const activeMessages = activeChatId == null ? [] : messagesByChatId[activeChatId] || [];

  return {
    status,
    progress,
    error,
    summary,
    chats,
    calls,
    activeChat,
    activeChatId,
    activeMessages,
    mediaUrls,
    mediaErrors,
    avatarUrls,
    avatarErrors,
    openArchive,
    closeArchive,
    selectChat,
    loadMedia,
    loadAvatar,
  };
}

export type WaviewArchiveController = ReturnType<typeof useWaviewArchive>;
export type MediaUrlRecord = ObjectUrlRecord;
export type { MediaBlobResult };
