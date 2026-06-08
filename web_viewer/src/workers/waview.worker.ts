/// <reference lib="webworker" />

import { normalizeZipPath } from "@/lib/format";
import { readZipDirectory, readZipEntryBlob, readZipEntryText, type ZipDirectory, type ZipEntryInfo } from "@/lib/zip-reader";
import type {
  ArchiveSummary,
  AvatarBlobResult,
  CallLog,
  Chat,
  ChatRow,
  Contact,
  MediaBlobResult,
  MediaEntry,
  Message as RawMessage,
  Poll,
  Reaction,
  ViewerCall,
  ViewerMessage,
  WaViewFile,
} from "@/types/waview";
import type { WorkerRequest, WorkerResponse } from "@/types/worker";

interface ActiveArchive {
  file: File;
  directory: ZipDirectory;
  data: WaViewFile;
  dataText?: string;
  messageArrayBounds?: JsonValueBounds;
  chats: ChatRow[];
  calls: ViewerCall[];
  contactsByJid: Map<string, Contact>;
  contactNameMap: Map<string, string>;
  avatarAliasMap: Map<string, string>;
  avatarEntriesByKey: Map<string, ZipEntryInfo>;
  fileNameIndex: Map<string, ZipEntryInfo[]>;
  rawChatsById: Map<number, Chat>;
  messagesByChatId: Map<number, ViewerMessage[]>;
  mediaByMessageId: Map<number, MediaEntry>;
  reactionsByMessageId: Map<number, Reaction[]>;
  pollsByMessageId: Map<number, Poll>;
  starredSet: Set<number>;
  avatarEntryByChatId: Map<number, ZipEntryInfo>;
}

interface JsonValueBounds {
  start: number;
  end: number;
}

const LARGE_DATA_JSON_THRESHOLD = 80 * 1024 * 1024;

let active: ActiveArchive | null = null;

function post(message: WorkerResponse) {
  self.postMessage(message);
}

function progress(phase: string, detail: string, value: number) {
  post({ type: "PROGRESS", phase, detail, progress: value });
}

function numeric(value: unknown, fallback = 0) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function stringValue(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function flag(value: unknown) {
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return value !== 0;
  if (typeof value === "string") return ["1", "true", "yes"].includes(value.trim().toLowerCase());
  return false;
}

function jidKey(jid = "") {
  return jid.trim().toLowerCase();
}

function normalizeContactKey(jid = "") {
  return jidKey(jid).split("@")[0];
}

function getContactName(jid: string, contactNameMap: Map<string, string>) {
  const normalizedJid = jidKey(jid);
  if (normalizedJid === "lid_me" || normalizedJid === "me") return "You";

  const phone = normalizeContactKey(normalizedJid);
  if (phone === "0") return "WhatsApp Business";

  return contactNameMap.get(normalizedJid) || contactNameMap.get(phone) || phone || "Unknown chat";
}

function displayNameForChat(chat: Chat, contactNameMap: Map<string, string>) {
  const jid = stringValue(chat.jid);
  return (
    stringValue(chat.chat_name) ||
    stringValue(chat.subject) ||
    getContactName(jid, contactNameMap) ||
    "Unknown chat"
  );
}

function lastMessageText(chat: Chat) {
  const text = stringValue(chat.last_message);
  if (text) return text;

  switch (numeric(chat.last_message_type)) {
    case 1:
      return "Photo";
    case 2:
      return "Audio";
    case 3:
      return "Video";
    case 5:
      return "Location";
    case 9:
      return "Document";
    case 20:
      return "Sticker";
    default:
      return "";
  }
}

function isVisibleMessage(message: ViewerMessage) {
  const systemish = message.isSystem || message.messageType === 7;
  return message.messageType !== 11 && !(systemish && !message.text && !message.media);
}

function messagePreviewText(message: ViewerMessage) {
  if (message.deletedForEveryone || message.isDeleted) return "Deleted message";
  if (message.text) return message.text;
  if (message.media?.caption) return message.media.caption;
  if (message.media?.file_name) return message.media.file_name;
  if (message.media) return lastMessageText({ last_message_type: message.messageType });
  if (message.isSystem || message.messageType === 7) return "";
  return lastMessageText({ last_message_type: message.messageType });
}

function lastVisibleMessageText(chat: Chat, messages: ViewerMessage[]) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (!isVisibleMessage(message)) continue;
    const preview = messagePreviewText(message);
    if (preview) return preview;
  }

  const fallback = lastMessageText(chat);
  return fallback === "System" ? "" : fallback;
}

function withMediaPrefix(relativePath = "") {
  const trimmed = normalizeZipPath(relativePath.replace(/^\/+/, ""));
  return trimmed.startsWith("media/") ? trimmed : `media/${trimmed}`;
}

function removeMediaPrefix(relativePath = "") {
  return normalizeZipPath(relativePath.replace(/^\/+/, "")).replace(/^media\//i, "");
}

function entryBaseName(path = "") {
  return normalizeZipPath(path).split("/").pop() || "";
}

function mediaFolderHintFromMime(mimeType = "") {
  const normalized = mimeType.toLowerCase();
  if (normalized.startsWith("image/")) return "images";
  if (normalized.startsWith("video/")) return "videos";
  if (normalized.startsWith("audio/")) return "audio";
  if (normalized.includes("webp")) return "stickers";
  if (normalized) return "documents";
  return "";
}

function folderHintFromMedia(media: MediaEntry) {
  const path = removeMediaPrefix(stringValue(media.relative_path));
  const lowerPath = path.toLowerCase();
  if (lowerPath.includes("sticker")) return "stickers";
  if (lowerPath.includes("image")) return "images";
  if (lowerPath.includes("video")) return "videos";
  if (lowerPath.includes("audio") || lowerPath.includes("voice")) return "audio";
  if (lowerPath.includes("document")) return "documents";
  return mediaFolderHintFromMime(stringValue(media.mime_type));
}

function mimeTypeToExtension(mimeType = "") {
  const normalized = mimeType.toLowerCase();
  if (normalized.includes("jpeg") || normalized.includes("jpg")) return "jpg";
  if (normalized.includes("png")) return "png";
  if (normalized.includes("gif")) return "gif";
  if (normalized.includes("webp")) return "webp";
  if (normalized.includes("mp4")) return "mp4";
  if (normalized.includes("3gpp")) return "3gp";
  if (normalized.includes("ogg")) return "ogg";
  if (normalized.includes("opus")) return "opus";
  if (normalized.includes("mpeg")) return "mp3";
  if (normalized.includes("pdf")) return "pdf";
  return "";
}

function buildFileNameIndex(directory: ZipDirectory) {
  const index = new Map<string, ZipEntryInfo[]>();
  for (const entry of directory.entries) {
    const normalizedName = normalizeZipPath(entry.name);
    if (!normalizedName.toLowerCase().startsWith("media/") || normalizedName.endsWith("/")) continue;
    const baseName = entryBaseName(normalizedName).toLowerCase();
    if (!baseName) continue;
    const matches = index.get(baseName) || [];
    matches.push(entry);
    index.set(baseName, matches);
  }
  return index;
}

function chooseIndexedEntry(matches: ZipEntryInfo[] | undefined, folderHint = "") {
  if (!matches?.length) return undefined;
  if (!folderHint) return matches[0];
  const normalizedHint = folderHint.toLowerCase();
  const hints =
    normalizedHint === "audio"
      ? ["audio", "voice notes"]
      : normalizedHint === "stickers"
        ? ["stickers", "sticker"]
        : normalizedHint === "images"
          ? ["images", "image"]
          : normalizedHint === "videos"
            ? ["videos", "video"]
            : [normalizedHint];
  return matches.find((entry) => hints.some((hint) => entry.normalizedName.toLowerCase().includes(hint))) || matches[0];
}

function inferRelativePathFromFileName(media: MediaEntry, fileNameIndex: Map<string, ZipEntryInfo[]>) {
  const fileName = stringValue(media.file_name);
  if (!fileName) return "";

  const match = chooseIndexedEntry(fileNameIndex.get(fileName.toLowerCase()), folderHintFromMedia(media));
  return match ? removeMediaPrefix(match.normalizedName) : "";
}

function mediaPathVariants(relativePath = "") {
  const cleaned = removeMediaPrefix(relativePath);
  if (!cleaned) return [];

  const variants = new Set<string>([cleaned]);
  const lower = cleaned.toLowerCase();

  if (lower.startsWith("whatsapp business/")) {
    variants.add(cleaned.replace(/^whatsapp business\//i, "WhatsApp/"));
  }

  if (lower.startsWith("whatsapp/")) {
    variants.add(cleaned.replace(/^whatsapp\//i, "WhatsApp Business/"));
  }

  const mediaSegmentIndex = lower.indexOf("/media/");
  if (mediaSegmentIndex > 0) {
    const mediaTail = cleaned.slice(mediaSegmentIndex + 1);
    variants.add(`WhatsApp/${mediaTail}`);
    variants.add(`WhatsApp Business/${mediaTail}`);
  }

  return [...variants].filter(Boolean);
}

function addMediaPathCandidates(candidates: Set<string>, relativePath = "", mimeExtension = "") {
  for (const variant of mediaPathVariants(relativePath)) {
    candidates.add(withMediaPrefix(variant));
    if (mimeExtension && !entryBaseName(variant).includes(".")) {
      candidates.add(withMediaPrefix(`${variant}.${mimeExtension}`));
    }

    const sentPath = variant.replace(/(^|\/)sent\//i, "$1");
    candidates.add(withMediaPrefix(sentPath));
    candidates.add(withMediaPrefix(`Sent/${sentPath}`));
  }
}

function normalizeMediaEntry(media: MediaEntry, directory: ZipDirectory, fileNameIndex: Map<string, ZipEntryInfo[]>): MediaEntry {
  const explicitRelativePath = removeMediaPrefix(stringValue(media.relative_path));
  const inferredRelativePath = explicitRelativePath || inferRelativePathFromFileName(media, fileNameIndex);
  const fileName = stringValue(media.file_name) || entryBaseName(inferredRelativePath);
  const mimeExtension = mimeTypeToExtension(stringValue(media.mime_type));
  const candidates = new Set<string>();

  addMediaPathCandidates(candidates, inferredRelativePath, mimeExtension);

  if (fileName) candidates.add(withMediaPrefix(fileName));

  const exactEntry = findEntry(directory, [...candidates]);
  const indexedEntry = exactEntry || chooseIndexedEntry(fileNameIndex.get(fileName.toLowerCase()), folderHintFromMedia(media));
  const resolvedPath = inferredRelativePath || (indexedEntry ? removeMediaPrefix(indexedEntry.normalizedName) : "");
  const status = indexedEntry ? "downloaded" : "not_embedded";

  return {
    ...media,
    relative_path: resolvedPath,
    file_name: fileName,
    status,
    size: numeric(media.size, indexedEntry?.uncompressedSize || 0),
  };
}

function resolveMediaEntry(activeArchive: ActiveArchive, media: MediaEntry) {
  const relativePath = removeMediaPrefix(stringValue(media.relative_path));
  const fileName = stringValue(media.file_name) || entryBaseName(relativePath);
  const mimeExtension = mimeTypeToExtension(stringValue(media.mime_type));
  const candidates = new Set<string>();

  addMediaPathCandidates(candidates, relativePath, mimeExtension);

  if (fileName) {
    candidates.add(withMediaPrefix(fileName));
  }

  const exact = findEntry(activeArchive.directory, [...candidates]);
  if (exact) return exact;

  if (fileName) {
    const indexed = chooseIndexedEntry(activeArchive.fileNameIndex.get(fileName.toLowerCase()), folderHintFromMedia(media));
    if (indexed) return indexed;
  }

  if (fileName) {
    const stem = fileName.replace(/\.[^.]+$/, "").toLowerCase();
    for (const [indexedName, matches] of activeArchive.fileNameIndex.entries()) {
      if (indexedName.replace(/\.[^.]+$/, "") === stem) {
        return chooseIndexedEntry(matches, folderHintFromMedia(media));
      }
    }
  }

  return undefined;
}

function avatarFilenameToJid(rawName: string) {
  if (rawName === "me.j" || rawName === "me") return "me";
  const withoutSuffix = rawName.replace(/\.j$/i, "");
  const separatorIndex = withoutSuffix.lastIndexOf("_");
  return separatorIndex > 0
    ? `${withoutSuffix.slice(0, separatorIndex)}@${withoutSuffix.slice(separatorIndex + 1)}`
    : withoutSuffix;
}

function buildAvatarJsonAlias(jid: string) {
  const normalized = jidKey(jid);
  if (normalized === "me" || normalized === "lid_me") return "me";
  const phone = normalizeContactKey(normalized);
  const server = normalized.split("@")[1]?.replace(/[.@]/g, "_") || "";
  return server ? `${phone}_${server}_j.jpeg` : phone;
}

function avatarLookupKeys(jid: string) {
  const exact = jidKey(jid);
  const phone = normalizeContactKey(exact);
  const keys = new Set<string>();
  if (exact) keys.add(exact);
  if (phone) {
    keys.add(phone);
    keys.add(`${phone}@s.whatsapp.net`);
    keys.add(`${phone}@g.us`);
    keys.add(buildAvatarJsonAlias(`${phone}@s.whatsapp.net`));
    keys.add(buildAvatarJsonAlias(`${phone}@g.us`));
  }
  if (exact === "me" || exact === "lid_me") keys.add("me");
  return [...keys].filter(Boolean);
}

function cacheAvatarAliases(avatarAliasMap: Map<string, string>, jid: string, avatarFile: string) {
  const alias = avatarFile.toLowerCase();
  if (!alias) return;
  const exact = jidKey(jid);
  const phone = normalizeContactKey(jid);
  if (exact) avatarAliasMap.set(exact, alias);
  if (phone) avatarAliasMap.set(phone, alias);
}

function buildAvatarEntryIndex(directory: ZipDirectory) {
  const avatarEntriesByKey = new Map<string, ZipEntryInfo>();
  for (const entry of directory.entries) {
    const normalizedName = normalizeZipPath(entry.name);
    if (!normalizedName.toLowerCase().startsWith("avatars/") || normalizedName.endsWith("/")) continue;
    const rawName = entryBaseName(normalizedName);
    const lowerRawName = rawName.toLowerCase();
    const jid = avatarFilenameToJid(lowerRawName);
    avatarEntriesByKey.set(lowerRawName, entry);
    avatarEntriesByKey.set(jidKey(jid), entry);
    avatarEntriesByKey.set(normalizeContactKey(jid), entry);
    avatarEntriesByKey.set(buildAvatarJsonAlias(jid), entry);
  }
  return avatarEntriesByKey;
}

function getAvatarEntryForJid(activeArchive: Pick<ActiveArchive, "avatarAliasMap" | "avatarEntriesByKey">, jid: string) {
  for (const key of avatarLookupKeys(jid)) {
    const alias = activeArchive.avatarAliasMap.get(key);
    if (alias) {
      const byAlias = activeArchive.avatarEntriesByKey.get(alias);
      if (byAlias) return byAlias;
    }

    const byKey = activeArchive.avatarEntriesByKey.get(key);
    if (byKey) return byKey;
  }
  return undefined;
}

function findEntry(directory: ZipDirectory, candidates: string[]) {
  for (const candidate of candidates) {
    const entry = directory.byName.get(normalizeZipPath(candidate));
    if (entry) return entry;
  }
  return undefined;
}

function skipWhitespace(json: string, index: number) {
  while (index < json.length && /\s/.test(json[index])) index += 1;
  return index;
}

function readStringToken(json: string, start: number) {
  let index = start + 1;
  let escaped = false;
  while (index < json.length) {
    const char = json[index];
    if (escaped) {
      escaped = false;
    } else if (char === "\\") {
      escaped = true;
    } else if (char === "\"") {
      return {
        value: JSON.parse(json.slice(start, index + 1)) as string,
        end: index + 1,
      };
    }
    index += 1;
  }
  throw new Error("Invalid data.json: unterminated string.");
}

function findJsonValueEnd(json: string, start: number) {
  const first = json[start];

  if (first === "{" || first === "[") {
    const open = first;
    const close = open === "{" ? "}" : "]";
    let depth = 0;
    let inString = false;
    let escaped = false;

    for (let index = start; index < json.length; index += 1) {
      const char = json[index];
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (char === "\\") {
          escaped = true;
        } else if (char === "\"") {
          inString = false;
        }
        continue;
      }

      if (char === "\"") {
        inString = true;
      } else if (char === open) {
        depth += 1;
      } else if (char === close) {
        depth -= 1;
        if (depth === 0) return index + 1;
      }
    }
    throw new Error("Invalid data.json: unterminated object or array.");
  }

  if (first === "\"") return readStringToken(json, start).end;

  let index = start;
  while (index < json.length && json[index] !== "," && json[index] !== "}") index += 1;
  return index;
}

function scanTopLevelValues(json: string, targetKeys: Set<string>) {
  const values = new Map<string, string>();
  let messageArrayBounds: JsonValueBounds | undefined;
  let index = skipWhitespace(json, 0);

  if (json[index] !== "{") throw new Error("Invalid data.json: expected top-level object.");
  index += 1;

  while (index < json.length) {
    index = skipWhitespace(json, index);
    if (json[index] === "}") break;
    if (json[index] === ",") {
      index += 1;
      continue;
    }
    if (json[index] !== "\"") throw new Error("Invalid data.json: expected top-level key.");

    const key = readStringToken(json, index);
    index = skipWhitespace(json, key.end);
    if (json[index] !== ":") throw new Error(`Invalid data.json: expected ':' after ${key.value}.`);
    index = skipWhitespace(json, index + 1);

    const valueStart = index;
    const valueEnd = findJsonValueEnd(json, valueStart);
    if (key.value === "messages") {
      messageArrayBounds = { start: valueStart, end: valueEnd };
    } else if (targetKeys.has(key.value)) {
      values.set(key.value, json.slice(valueStart, valueEnd));
    }
    index = valueEnd;
  }

  return { values, messageArrayBounds };
}

function parseScannedValue<T>(values: Map<string, string>, key: string, fallback: T): T {
  const value = values.get(key);
  if (!value) return fallback;
  return JSON.parse(value) as T;
}

function forEachArrayItem(json: string, bounds: JsonValueBounds, visit: (itemText: string) => void) {
  let index = skipWhitespace(json, bounds.start);
  if (json[index] !== "[") throw new Error("Invalid data.json: expected messages array.");
  index += 1;

  while (index < bounds.end) {
    index = skipWhitespace(json, index);
    if (json[index] === "]") break;
    if (json[index] === ",") {
      index += 1;
      continue;
    }

    const itemStart = index;
    const itemEnd = findJsonValueEnd(json, itemStart);
    visit(json.slice(itemStart, itemEnd));
    index = itemEnd;
  }
}

async function readTextEntry(file: File, directory: ZipDirectory, name: string) {
  const entry = directory.byName.get(normalizeZipPath(name));
  if (!entry) throw new Error(`Invalid .waview: missing ${name}`);

  return readZipEntryText(file, entry);
}

function normalizeMessage(
  message: RawMessage,
  mediaByMessageId: Map<number, MediaEntry>,
  reactionsByMessageId: Map<number, Reaction[]>,
  pollsByMessageId: Map<number, Poll>,
): ViewerMessage {
  const id = numeric(message.id);
  return {
    id,
    chatId: numeric(message.chat_id),
    chatJid: stringValue(message.chat_jid),
    text: stringValue(message.text_data),
    fromMe: flag(message.from_me),
    timestamp: numeric(message.timestamp),
    messageType: numeric(message.message_type),
    status: numeric(message.status),
    senderName: stringValue(message.sender_name),
    senderJid: stringValue(message.sender_jid),
    isSystem: flag(message.is_system),
    isDeleted: flag(message.is_deleted),
    deletedForEveryone: flag(message.deleted_for_everyone),
    isForwarded: flag(message.is_forwarded),
    starred: flag(message.starred),
    quotedText: stringValue(message.quoted_text),
    quotedSenderName: stringValue(message.quoted_sender_name),
    placeName: stringValue(message.place_name),
    placeAddress: stringValue(message.place_address),
    latitude: numeric(message.latitude),
    longitude: numeric(message.longitude),
    media: mediaByMessageId.get(id),
    reactions: reactionsByMessageId.get(id) || [],
    poll: pollsByMessageId.get(id),
  };
}

function buildIndexes(file: File, directory: ZipDirectory, data: WaViewFile, version: { formatVersion?: number; createdBy?: string }) {
  const contactsByJid = new Map<string, Contact>();
  const contactNameMap = new Map<string, string>();
  const avatarAliasMap = new Map<string, string>();
  const avatarEntriesByKey = buildAvatarEntryIndex(directory);
  const fileNameIndex = buildFileNameIndex(directory);
  const rawChatsById = new Map<number, Chat>();
  const mediaByMessageId = new Map<number, MediaEntry>();
  const reactionsByMessageId = new Map<number, Reaction[]>();
  const pollsByMessageId = new Map<number, Poll>();
  const avatarEntryByChatId = new Map<number, ZipEntryInfo>();
  const messagesByChatId = new Map<number, ViewerMessage[]>();

  for (const contact of data.contacts || []) {
    const jid = stringValue(contact.jid);
    if (!jid) continue;

    const exactJid = jidKey(jid);
    const phone = normalizeContactKey(jid);
    const name = stringValue(contact.display_name) || stringValue(contact.wa_name) || phone;
    contactsByJid.set(exactJid, contact);
    if (name) {
      contactNameMap.set(exactJid, name);
      contactNameMap.set(phone, name);
    }
    cacheAvatarAliases(avatarAliasMap, jid, stringValue(contact.avatar_file));
  }

  for (const media of data.media_index || []) {
    const messageId = numeric(media.message_id);
    if (messageId) mediaByMessageId.set(messageId, normalizeMediaEntry(media, directory, fileNameIndex));
  }

  for (const reaction of data.reactions || []) {
    const messageId = numeric(reaction.message_id);
    if (!messageId) continue;
    const list = reactionsByMessageId.get(messageId) || [];
    list.push(reaction);
    reactionsByMessageId.set(messageId, list);
  }

  for (const poll of data.polls || []) {
    const messageId = numeric(poll.message_id);
    if (messageId) pollsByMessageId.set(messageId, poll);
  }

  progress("Indexing messages", "Linking messages to chats, media, reactions and polls", 58);
  for (const message of data.messages || []) {
    const viewerMessage = normalizeMessage(message, mediaByMessageId, reactionsByMessageId, pollsByMessageId);
    const list = messagesByChatId.get(viewerMessage.chatId) || [];
    list.push(viewerMessage);
    messagesByChatId.set(viewerMessage.chatId, list);
  }

  for (const list of messagesByChatId.values()) {
    list.sort((a, b) => (a.timestamp || a.id) - (b.timestamp || b.id));
  }

  const starredSet = new Set((data.starred_messages || []).map((value) => numeric(value)).filter(Boolean));
  const calls: ViewerCall[] = (data.call_logs || [])
    .map((call: CallLog) => {
      const title = stringValue(call.chat_subject) || getContactName(stringValue(call.jid), contactNameMap);
      return {
        id: numeric(call.id),
        jid: stringValue(call.jid),
        title,
        timestamp: numeric(call.timestamp),
        duration: numeric(call.duration),
        fromMe: flag(call.from_me),
        isVideo: flag(call.is_video),
        result: numeric(call.call_result),
      };
    })
    .sort((a, b) => b.timestamp - a.timestamp);

  progress("Indexing chats", "Preparing chat rows and avatar lookups", 72);
  const chats = (data.chats || [])
    .flatMap((chat): ChatRow[] => {
      const id = numeric(chat.id);
      rawChatsById.set(id, chat);
      const jid = stringValue(chat.jid);
      const avatarFile = stringValue(chat.avatar_file);

      if (avatarFile) cacheAvatarAliases(avatarAliasMap, jid, avatarFile);

      const messages = messagesByChatId.get(id) || [];
      const visibleMessages = messages.filter(isVisibleMessage);
      const hasVisibleMessage = visibleMessages.length > 0;
      if (!hasVisibleMessage) return [];

      const mediaCount = visibleMessages.filter((message) => message.media).length;
      const starredCount = visibleMessages.filter((message) => message.starred || starredSet.has(message.id)).length;
      const title = displayNameForChat(chat, contactNameMap);
      const avatarEntry =
        getAvatarEntryForJid({ avatarAliasMap, avatarEntriesByKey }, jid) ||
        (avatarFile ? findEntry(directory, [`avatars/${avatarFile}`, avatarFile]) : undefined);

      if (avatarEntry) avatarEntryByChatId.set(id, avatarEntry);

      return [{
        id,
        jid,
        title,
        subtitle: lastVisibleMessageText(chat, visibleMessages),
        timestamp: numeric(chat.sort_timestamp),
        lastMessageType: numeric(chat.last_message_type),
        unreadCount: numeric(chat.unread_count),
        isGroup: flag(chat.is_group),
        memberCount: numeric(chat.member_count),
        avatarFile: avatarFile || (avatarEntry ? entryBaseName(avatarEntry.name) : ""),
        messageCount: visibleMessages.length,
        mediaCount,
        starredCount,
        hasDownloadedMedia: visibleMessages.some((message) => message.media?.status === "downloaded"),
        archived: flag(chat.archived),
        pinned: flag(chat.pinned),
      }];
    })
    .sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.timestamp - a.timestamp || b.id - a.id);

  const normalizedMediaEntries = [...mediaByMessageId.values()];
  const downloadedMedia = normalizedMediaEntries.filter((entry) => entry.status === "downloaded").length;
  const missingMedia = normalizedMediaEntries.length - downloadedMedia;
  const summary: ArchiveSummary = {
    fileName: file.name,
    fileSize: file.size,
    formatVersion: numeric(version.formatVersion, numeric(data.export_info?.format_version, 3)),
    createdBy: stringValue(version.createdBy, "WA Sensai"),
    exportInfo: data.export_info || {},
    counts: {
      chats: chats.length,
      groups: chats.filter((chat) => chat.isGroup).length,
      messages: (data.messages || []).length,
      media: (data.media_index || []).length,
      downloadedMedia,
      missingMedia,
      calls: calls.length,
      starred: (data.starred_messages || []).length || chats.reduce((count, chat) => count + chat.starredCount, 0),
      reactions: (data.reactions || []).length,
      polls: (data.polls || []).length,
      contacts: (data.contacts || []).length,
      labels: (data.labels || []).length,
      labeledMessages: (data.labeled_messages || []).length,
      mentions: (data.mentions || []).length,
      vcards: (data.vcards || []).length,
      statuses: (data.statuses || []).length,
      messageEdits: (data.message_edits || []).length,
    },
    warnings: [
      ...(directory.isZip64 ? ["ZIP64 archive detected; large-file mode is active."] : []),
      ...(normalizedMediaEntries.some((media) => !media.relative_path)
        ? ["Some media rows have empty paths; they can only preview if the file name exists inside the archive."]
        : []),
      ...(normalizedMediaEntries.some((media) => media.status !== "downloaded")
        ? ["Some referenced media is not embedded in this .waview file and cannot be opened by the viewer."]
        : []),
      ...((data.vcards || []).length || (data.labels || []).length || (data.mentions || []).length || (data.statuses || []).length
        ? ["Labels, mentions, vCards, and statuses are included as archive metadata; this viewer surfaces their counts but does not render dedicated detail pages yet."]
        : []),
    ],
  };

  return {
    summary,
    archive: {
      file,
      directory,
      data,
      chats,
      calls,
      contactsByJid,
      contactNameMap,
      avatarAliasMap,
      avatarEntriesByKey,
      fileNameIndex,
      rawChatsById,
      messagesByChatId,
      mediaByMessageId,
      reactionsByMessageId,
      pollsByMessageId,
      starredSet,
      avatarEntryByChatId,
    } satisfies ActiveArchive,
  };
}

function buildLazyIndexes(
  file: File,
  directory: ZipDirectory,
  data: WaViewFile,
  version: { formatVersion?: number; createdBy?: string },
  dataText: string,
  messageArrayBounds?: JsonValueBounds,
) {
  const contactsByJid = new Map<string, Contact>();
  const contactNameMap = new Map<string, string>();
  const avatarAliasMap = new Map<string, string>();
  const avatarEntriesByKey = buildAvatarEntryIndex(directory);
  const fileNameIndex = buildFileNameIndex(directory);
  const rawChatsById = new Map<number, Chat>();
  const mediaByMessageId = new Map<number, MediaEntry>();
  const reactionsByMessageId = new Map<number, Reaction[]>();
  const pollsByMessageId = new Map<number, Poll>();
  const avatarEntryByChatId = new Map<number, ZipEntryInfo>();
  const messagesByChatId = new Map<number, ViewerMessage[]>();
  const starredSet = new Set((data.starred_messages || []).map((value) => numeric(value)).filter(Boolean));

  progress("Indexing metadata", "Preparing contacts and avatars", 74);
  for (const contact of data.contacts || []) {
    const jid = stringValue(contact.jid);
    if (!jid) continue;

    const exactJid = jidKey(jid);
    const phone = normalizeContactKey(jid);
    const name = stringValue(contact.display_name) || stringValue(contact.wa_name) || phone;
    contactsByJid.set(exactJid, contact);
    if (name) {
      contactNameMap.set(exactJid, name);
      contactNameMap.set(phone, name);
    }
    cacheAvatarAliases(avatarAliasMap, jid, stringValue(contact.avatar_file));
  }

  progress("Indexing metadata", `Resolving ${(data.media_index || []).length.toLocaleString()} media references`, 78);
  for (const media of data.media_index || []) {
    const messageId = numeric(media.message_id);
    if (messageId) mediaByMessageId.set(messageId, normalizeMediaEntry(media, directory, fileNameIndex));
  }

  progress("Indexing metadata", "Preparing reactions and polls", 84);
  for (const reaction of data.reactions || []) {
    const messageId = numeric(reaction.message_id);
    if (!messageId) continue;
    const list = reactionsByMessageId.get(messageId) || [];
    list.push(reaction);
    reactionsByMessageId.set(messageId, list);
  }

  for (const poll of data.polls || []) {
    const messageId = numeric(poll.message_id);
    if (messageId) pollsByMessageId.set(messageId, poll);
  }

  progress("Indexing metadata", "Preparing call history", 88);
  const calls: ViewerCall[] = (data.call_logs || [])
    .map((call: CallLog) => {
      const title = stringValue(call.chat_subject) || getContactName(stringValue(call.jid), contactNameMap);
      return {
        id: numeric(call.id),
        jid: stringValue(call.jid),
        title,
        timestamp: numeric(call.timestamp),
        duration: numeric(call.duration),
        fromMe: flag(call.from_me),
        isVideo: flag(call.is_video),
        result: numeric(call.call_result),
      };
    })
    .sort((a, b) => b.timestamp - a.timestamp);

  progress("Indexing metadata", `Preparing ${(data.chats || []).length.toLocaleString()} chats`, 92);
  const chats = (data.chats || [])
    .map((chat): ChatRow => {
      const id = numeric(chat.id);
      rawChatsById.set(id, chat);
      const jid = stringValue(chat.jid);
      const avatarFile = stringValue(chat.avatar_file);

      if (avatarFile) cacheAvatarAliases(avatarAliasMap, jid, avatarFile);
      const title = displayNameForChat(chat, contactNameMap);
      const avatarEntry =
        getAvatarEntryForJid({ avatarAliasMap, avatarEntriesByKey }, jid) ||
        (avatarFile ? findEntry(directory, [`avatars/${avatarFile}`, avatarFile]) : undefined);

      if (avatarEntry) avatarEntryByChatId.set(id, avatarEntry);

      return {
        id,
        jid,
        title,
        subtitle: lastMessageText(chat) || "Open chat",
        timestamp: numeric(chat.sort_timestamp),
        lastMessageType: numeric(chat.last_message_type),
        unreadCount: numeric(chat.unread_count),
        isGroup: flag(chat.is_group),
        memberCount: numeric(chat.member_count),
        avatarFile: avatarFile || (avatarEntry ? entryBaseName(avatarEntry.name) : ""),
        messageCount: 0,
        mediaCount: 0,
        starredCount: 0,
        hasDownloadedMedia: false,
        archived: flag(chat.archived),
        pinned: flag(chat.pinned),
      };
    })
    .sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.timestamp - a.timestamp || b.id - a.id);

  const normalizedMediaEntries = [...mediaByMessageId.values()];
  const downloadedMedia = normalizedMediaEntries.filter((entry) => entry.status === "downloaded").length;
  const missingMedia = normalizedMediaEntries.length - downloadedMedia;
  const summary: ArchiveSummary = {
    fileName: file.name,
    fileSize: file.size,
    formatVersion: numeric(version.formatVersion, numeric(data.export_info?.format_version, 3)),
    createdBy: stringValue(version.createdBy, "WA Sensai"),
    exportInfo: data.export_info || {},
    counts: {
      chats: chats.length,
      groups: chats.filter((chat) => chat.isGroup).length,
      messages: numeric(data.export_info?.total_messages),
      media: (data.media_index || []).length,
      downloadedMedia,
      missingMedia,
      calls: calls.length,
      starred: starredSet.size,
      reactions: (data.reactions || []).length,
      polls: (data.polls || []).length,
      contacts: (data.contacts || []).length,
      labels: (data.labels || []).length,
      labeledMessages: (data.labeled_messages || []).length,
      mentions: (data.mentions || []).length,
      vcards: (data.vcards || []).length,
      statuses: (data.statuses || []).length,
      messageEdits: (data.message_edits || []).length,
    },
    warnings: [
      "Large archive mode is active; messages are parsed when a chat is opened.",
      ...(directory.isZip64 ? ["ZIP64 archive detected; large-file mode is active."] : []),
      ...(normalizedMediaEntries.some((media) => !media.relative_path)
        ? ["Some media rows have empty paths; they can only preview if the file name exists inside the archive."]
        : []),
      ...(normalizedMediaEntries.some((media) => media.status !== "downloaded")
        ? ["Some referenced media is not embedded in this .waview file and cannot be opened by the viewer."]
        : []),
    ],
  };

  return {
    summary,
    archive: {
      file,
      directory,
      data,
      dataText,
      messageArrayBounds,
      chats,
      calls,
      contactsByJid,
      contactNameMap,
      avatarAliasMap,
      avatarEntriesByKey,
      fileNameIndex,
      rawChatsById,
      messagesByChatId,
      mediaByMessageId,
      reactionsByMessageId,
      pollsByMessageId,
      starredSet,
      avatarEntryByChatId,
    } satisfies ActiveArchive,
  };
}

async function openArchive(file: File) {
  progress("Opening archive", "Reading ZIP directory from the end of the file", 8);
  const directory = await readZipDirectory(file);

  progress("Validating", "Checking WA Sensai metadata", 24);
  const versionText = await readTextEntry(file, directory, "meta/version.json");
  const version = JSON.parse(versionText || "{}") as { formatVersion?: number; createdBy?: string };
  if (numeric(version.formatVersion) !== 3) {
    throw new Error(`Unsupported .waview format version ${version.formatVersion ?? "unknown"}. Expected version 3.`);
  }

  const dataEntry = directory.byName.get(normalizeZipPath("data.json"));
  if (!dataEntry) throw new Error("Invalid .waview: missing data.json");
  const largeArchiveMode = dataEntry.uncompressedSize >= LARGE_DATA_JSON_THRESHOLD;

  progress(
    "Loading data",
    largeArchiveMode
      ? `Reading large data.json (${Math.round(dataEntry.uncompressedSize / 1024 / 1024)} MB)`
      : "Extracting data.json without touching media files",
    38,
  );
  const dataText = await readTextEntry(file, directory, "data.json");

  if (largeArchiveMode) {
    progress("Parsing metadata", "Skipping message bodies until a chat is opened", 48);
    const keys = new Set([
      "export_info",
      "chats",
      "contacts",
      "media_index",
      "call_logs",
      "reactions",
      "polls",
      "labels",
      "labeled_messages",
      "mentions",
      "vcards",
      "statuses",
      "message_edits",
      "starred_messages",
    ]);
    const scanned = scanTopLevelValues(dataText, keys);
    const data: WaViewFile = {
      export_info: parseScannedValue(scanned.values, "export_info", {}),
      chats: parseScannedValue(scanned.values, "chats", []),
      contacts: parseScannedValue(scanned.values, "contacts", []),
      media_index: parseScannedValue(scanned.values, "media_index", []),
      call_logs: parseScannedValue(scanned.values, "call_logs", []),
      reactions: parseScannedValue(scanned.values, "reactions", []),
      polls: parseScannedValue(scanned.values, "polls", []),
      labels: parseScannedValue(scanned.values, "labels", []),
      labeled_messages: parseScannedValue(scanned.values, "labeled_messages", []),
      mentions: parseScannedValue(scanned.values, "mentions", []),
      vcards: parseScannedValue(scanned.values, "vcards", []),
      statuses: parseScannedValue(scanned.values, "statuses", []),
      message_edits: parseScannedValue(scanned.values, "message_edits", []),
      starred_messages: parseScannedValue(scanned.values, "starred_messages", []),
      messages: [],
    };

    progress("Indexing metadata", "Preparing chats, contacts, calls and media references", 72);
    const { archive, summary } = buildLazyIndexes(file, directory, data, version, dataText, scanned.messageArrayBounds);
    active = archive;
    progress("Ready", "Large archive is ready; messages load per chat", 100);
    post({ type: "ARCHIVE_READY", summary, chats: archive.chats, calls: archive.calls });
    return;
  }

  const data = JSON.parse(dataText || "{}") as WaViewFile;

  const { archive, summary } = buildIndexes(file, directory, data, version);
  active = archive;

  progress("Ready", "Viewer index is ready", 100);
  post({ type: "ARCHIVE_READY", summary, chats: archive.chats, calls: archive.calls });
}

async function getMessages(chatId: number) {
  if (!active) throw new Error("No archive is open.");
  const cached = active.messagesByChatId.get(chatId);
  if (cached) {
    post({ type: "MESSAGES_READY", chatId, messages: cached.filter(isVisibleMessage) });
    return;
  }

  if (active.dataText && active.messageArrayBounds) {
    const messages: ViewerMessage[] = [];
    const chatPattern = new RegExp(`"chat_id"\\s*:\\s*${chatId}(?=\\s*[,}])`);

    forEachArrayItem(active.dataText, active.messageArrayBounds, (itemText) => {
      if (!chatPattern.test(itemText)) return;
      const rawMessage = JSON.parse(itemText) as RawMessage;
      const viewerMessage = normalizeMessage(
        rawMessage,
        active?.mediaByMessageId || new Map<number, MediaEntry>(),
        active?.reactionsByMessageId || new Map<number, Reaction[]>(),
        active?.pollsByMessageId || new Map<number, Poll>(),
      );
      if (active?.starredSet.has(viewerMessage.id)) viewerMessage.starred = true;
      messages.push(viewerMessage);
    });

    messages.sort((a, b) => (a.timestamp || a.id) - (b.timestamp || b.id));
    active.messagesByChatId.set(chatId, messages);
    const chat = active.chats.find((item) => item.id === chatId);
    if (chat) {
      chat.messageCount = messages.filter(isVisibleMessage).length;
      chat.mediaCount = messages.filter((message) => isVisibleMessage(message) && message.media).length;
      chat.starredCount = messages.filter((message) => isVisibleMessage(message) && message.starred).length;
      chat.hasDownloadedMedia = messages.some((message) => isVisibleMessage(message) && message.media?.status === "downloaded");
    }
    post({ type: "MESSAGES_READY", chatId, messages: messages.filter(isVisibleMessage) });
    return;
  }

  post({ type: "MESSAGES_READY", chatId, messages: [] });
}

async function readEntryBlob(file: File, entry: ZipEntryInfo, mimeType: string) {
  return readZipEntryBlob(file, entry, mimeType);
}

async function getMedia(messageId: number) {
  if (!active) throw new Error("No archive is open.");
  const media = active.mediaByMessageId.get(messageId);
  if (!media) throw new Error("This message has no media entry.");

  const entry = resolveMediaEntry(active, media);
  if (!entry) throw new Error("This media file is referenced by data.json but is not embedded in the archive.");

  const mimeType = stringValue(media.mime_type, "application/octet-stream");
  const blob = await readEntryBlob(active.file, entry, mimeType);
  const result: MediaBlobResult = {
    messageId,
    blob,
    mimeType,
    fileName: stringValue(media.file_name) || entry.name.split("/").pop() || "media",
    size: numeric(media.size, entry.uncompressedSize),
  };
  post({ type: "MEDIA_READY", media: result });
}

async function getAvatar(chatId: number) {
  if (!active) throw new Error("No archive is open.");
  const chat = active.rawChatsById.get(chatId);
  const entry = active.avatarEntryByChatId.get(chatId) || (chat ? getAvatarEntryForJid(active, stringValue(chat.jid)) : undefined);
  if (!entry) throw new Error("No avatar is available for this chat.");

  const blob = await readEntryBlob(active.file, entry, "image/jpeg");
  const result: AvatarBlobResult = { chatId, blob };
  post({ type: "AVATAR_READY", avatar: result });
}

function closeArchive() {
  active = null;
  post({ type: "CLOSED" });
}

self.onmessage = async (event: MessageEvent<WorkerRequest>) => {
  try {
    const request = event.data;
    if (request.type === "OPEN_ARCHIVE") await openArchive(request.file);
    if (request.type === "GET_MESSAGES") await getMessages(request.chatId);
    if (request.type === "GET_MEDIA") {
      try {
        await getMedia(request.messageId);
      } catch (error) {
        post({
          type: "MEDIA_ERROR",
          messageId: request.messageId,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }
    if (request.type === "GET_AVATAR") {
      try {
        await getAvatar(request.chatId);
      } catch (error) {
        post({
          type: "AVATAR_ERROR",
          chatId: request.chatId,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    }
    if (request.type === "CLOSE_ARCHIVE") closeArchive();
  } catch (error) {
    post({ type: "ERROR", message: error instanceof Error ? error.message : String(error) });
  }
};
