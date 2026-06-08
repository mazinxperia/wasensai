import type { ArchiveSummary, AvatarBlobResult, ChatRow, MediaBlobResult, ViewerCall, ViewerMessage } from "@/types/waview";

export type ChatFilter = "all" | "groups" | "media" | "starred" | "calls";

export interface OpenArchiveRequest {
  type: "OPEN_ARCHIVE";
  file: File;
}

export interface GetMessagesRequest {
  type: "GET_MESSAGES";
  chatId: number;
}

export interface GetMediaRequest {
  type: "GET_MEDIA";
  messageId: number;
}

export interface GetAvatarRequest {
  type: "GET_AVATAR";
  chatId: number;
}

export interface CloseArchiveRequest {
  type: "CLOSE_ARCHIVE";
}

export type WorkerRequest =
  | OpenArchiveRequest
  | GetMessagesRequest
  | GetMediaRequest
  | GetAvatarRequest
  | CloseArchiveRequest;

export interface WorkerProgress {
  type: "PROGRESS";
  phase: string;
  detail: string;
  progress: number;
}

export interface WorkerArchiveReady {
  type: "ARCHIVE_READY";
  summary: ArchiveSummary;
  chats: ChatRow[];
  calls: ViewerCall[];
}

export interface WorkerMessagesReady {
  type: "MESSAGES_READY";
  chatId: number;
  messages: ViewerMessage[];
}

export interface WorkerMediaReady {
  type: "MEDIA_READY";
  media: MediaBlobResult;
}

export interface WorkerMediaError {
  type: "MEDIA_ERROR";
  messageId: number;
  message: string;
}

export interface WorkerAvatarReady {
  type: "AVATAR_READY";
  avatar: AvatarBlobResult;
}

export interface WorkerAvatarError {
  type: "AVATAR_ERROR";
  chatId: number;
  message: string;
}

export interface WorkerClosed {
  type: "CLOSED";
}

export interface WorkerError {
  type: "ERROR";
  message: string;
}

export type WorkerResponse =
  | WorkerProgress
  | WorkerArchiveReady
  | WorkerMessagesReady
  | WorkerMediaReady
  | WorkerMediaError
  | WorkerAvatarReady
  | WorkerAvatarError
  | WorkerClosed
  | WorkerError;
