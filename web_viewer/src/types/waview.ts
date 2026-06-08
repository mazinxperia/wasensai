export interface ExportInfo {
  phone_number?: string;
  export_date?: string;
  app_version?: string;
  format_version?: number;
  total_chats?: number;
  total_messages?: number;
  total_media?: number;
}

export interface Chat {
  id?: number;
  jid?: string;
  subject?: string;
  chat_name?: string;
  is_group?: boolean;
  sort_timestamp?: number;
  last_message?: string;
  last_message_type?: number;
  member_count?: number;
  avatar_file?: string;
  archived?: boolean;
  unread_count?: number;
  ephemeral_expiration?: number;
  created_timestamp?: number;
  pinned?: boolean;
  muted_until?: number;
}

export interface Contact {
  jid?: string;
  display_name?: string;
  wa_name?: string;
  status?: string;
  avatar_file?: string;
}

export interface Group {
  jid?: string;
  subject?: string;
  owner_jid?: string;
  creation?: number;
  participants?: GroupParticipant[];
}

export interface GroupParticipant {
  jid?: string;
  is_admin?: boolean;
  is_super_admin?: boolean;
}

export interface Message {
  id?: number;
  chat_id?: number;
  chat_jid?: string;
  text_data?: string;
  from_me?: number;
  timestamp?: number;
  received_timestamp?: number;
  sort_id?: number;
  message_type?: number;
  status?: number;
  edit_version?: number;
  key_id?: string;
  sender_jid?: string;
  sender_name?: string;
  chat_subject?: string;
  is_deleted?: boolean;
  is_system?: boolean;
  is_video_call?: boolean;
  is_forwarded?: boolean;
  forward_score?: number;
  starred?: number;
  broadcast?: number;
  translated_text?: string;
  latitude?: number;
  longitude?: number;
  quoted_key_id?: string;
  quoted_from_me?: number;
  quoted_message_type?: number;
  quoted_text?: string;
  quoted_sender?: string;
  quoted_sender_name?: string;
  action_type?: number;
  deleted_for_everyone?: boolean;
  place_name?: string;
  place_address?: string;
}

export interface MediaEntry {
  message_id?: number;
  relative_path?: string;
  mime_type?: string;
  size?: number;
  file_name?: string;
  caption?: string;
  duration?: number;
  width?: number;
  height?: number;
  transferred?: number;
  status?: string;
  media_hash?: string;
}

export interface Reaction {
  message_id?: number;
  emoji?: string;
  sender_jid?: string;
  timestamp?: number;
}

export interface Poll {
  message_id?: number;
  question?: string;
  selectable_count?: number;
  end_time?: number;
  options?: PollOptionEntry[];
  votes?: PollVote[];
}

export interface PollOptionEntry {
  id?: number;
  option_name?: string;
  vote_total?: number;
}

export interface PollVote {
  voter_jid?: string;
  option_ids?: string;
  timestamp?: number;
}

export interface CallLog {
  id?: number;
  jid?: string;
  timestamp?: number;
  duration?: number;
  from_me?: boolean;
  call_id?: string;
  call_result?: number;
  chat_subject?: string;
  is_video?: boolean;
  participants?: CallParticipant[];
}

export interface CallParticipant {
  jid?: string;
  call_result?: number;
  duration?: number;
}

export interface Label {
  id?: number;
  name?: string;
  color?: number;
}

export interface LabeledMessage {
  label_id?: number;
  message_id?: number;
}

export interface Mention {
  message_id?: number;
  jid?: string;
}

export interface VCard {
  message_id?: number;
  display_name?: string;
  vcard?: string;
}

export interface StatusUpdate {
  id?: number;
  jid?: string;
  timestamp?: number;
  text_data?: string;
  message_type?: number;
}

export interface MessageEdit {
  message_id?: number;
  edit_timestamp?: number;
  text_data?: string;
}

export interface WaViewFile {
  export_info?: ExportInfo;
  chats?: Chat[];
  contacts?: Contact[];
  groups?: Group[];
  messages?: Message[];
  reactions?: Reaction[];
  polls?: Poll[];
  media_index?: MediaEntry[];
  call_logs?: CallLog[];
  labels?: Label[];
  labeled_messages?: LabeledMessage[];
  mentions?: Mention[];
  vcards?: VCard[];
  statuses?: StatusUpdate[];
  message_edits?: MessageEdit[];
  starred_messages?: number[];
}

export interface ArchiveSummary {
  fileName: string;
  fileSize: number;
  formatVersion: number;
  createdBy: string;
  exportInfo: ExportInfo;
  counts: {
    chats: number;
    groups: number;
    messages: number;
    media: number;
    downloadedMedia: number;
    missingMedia: number;
    calls: number;
    starred: number;
    reactions: number;
    polls: number;
    contacts: number;
    labels: number;
    labeledMessages: number;
    mentions: number;
    vcards: number;
    statuses: number;
    messageEdits: number;
  };
  warnings: string[];
}

export interface ChatRow {
  id: number;
  jid: string;
  title: string;
  subtitle: string;
  timestamp: number;
  lastMessageType: number;
  unreadCount: number;
  isGroup: boolean;
  memberCount: number;
  avatarFile: string;
  messageCount: number;
  mediaCount: number;
  starredCount: number;
  hasDownloadedMedia: boolean;
  archived: boolean;
  pinned: boolean;
}

export interface ViewerMessage {
  id: number;
  chatId: number;
  chatJid: string;
  text: string;
  fromMe: boolean;
  timestamp: number;
  messageType: number;
  status: number;
  senderName: string;
  senderJid: string;
  isSystem: boolean;
  isDeleted: boolean;
  deletedForEveryone: boolean;
  isForwarded: boolean;
  starred: boolean;
  quotedText: string;
  quotedSenderName: string;
  placeName: string;
  placeAddress: string;
  latitude: number;
  longitude: number;
  media?: MediaEntry;
  reactions: Reaction[];
  poll?: Poll;
}

export interface ViewerCall {
  id: number;
  jid: string;
  title: string;
  timestamp: number;
  duration: number;
  fromMe: boolean;
  isVideo: boolean;
  result: number;
}

export interface MediaBlobResult {
  messageId: number;
  blob: Blob;
  mimeType: string;
  fileName: string;
  size: number;
}

export interface AvatarBlobResult {
  chatId: number;
  blob: Blob;
}
