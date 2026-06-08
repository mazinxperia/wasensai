const dateTime = new Intl.DateTimeFormat(undefined, {
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const shortDate = new Intl.DateTimeFormat(undefined, {
  day: "2-digit",
  month: "short",
});

const timeOnly = new Intl.DateTimeFormat(undefined, {
  hour: "2-digit",
  minute: "2-digit",
});

export function formatBytes(bytes = 0) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value >= 10 || index === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`;
}

export function formatTimestamp(timestamp = 0, fallback = "") {
  if (!timestamp) return fallback;
  return dateTime.format(new Date(timestamp));
}

export function formatChatDate(timestamp = 0) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  return sameDay ? timeOnly.format(date) : shortDate.format(date);
}

export function formatMessageTime(timestamp = 0) {
  if (!timestamp) return "";
  return timeOnly.format(new Date(timestamp));
}

export function formatDuration(msOrSeconds = 0) {
  const seconds = msOrSeconds > 10_000 ? Math.round(msOrSeconds / 1000) : Math.round(msOrSeconds);
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return `${minutes}:${remaining.toString().padStart(2, "0")}`;
}

export function messageTypeLabel(type = 0) {
  switch (type) {
    case 1:
      return "Photo";
    case 2:
      return "Audio";
    case 3:
      return "Video";
    case 5:
      return "Location";
    case 7:
      return "System";
    case 9:
      return "Document";
    case 13:
      return "GIF";
    case 11:
      return "Call";
    case 15:
      return "Deleted";
    case 20:
      return "Sticker";
    case 84:
      return "View once photo";
    case 85:
      return "View once video";
    default:
      return "Message";
  }
}

export function mimeMajor(mime = "") {
  return mime.split("/")[0]?.toLowerCase() || "";
}

export function isPreviewableImage(mime = "", name = "") {
  const ext = name.toLowerCase().split(".").pop();
  return mime.startsWith("image/") && ext !== "heic";
}

export function normalizeZipPath(path = "") {
  return path.replace(/\\/g, "/").replace(/^\/+/, "").toLowerCase();
}

export function initialsFor(value = "") {
  const clean = value.trim();
  if (!clean) return "WA";
  const parts = clean.split(/\s+/).filter(Boolean);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
}
