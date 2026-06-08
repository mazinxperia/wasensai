import { AlertTriangle, Archive, FileImage, HardDrive, MessageCircle, Phone, Star, Users } from "lucide-react";

import { ChatAvatar } from "@/components/viewer/ChatAvatar";
import { StatPill } from "@/components/viewer/StatPill";
import { formatBytes, formatTimestamp } from "@/lib/format";
import type { ArchiveSummary, ChatRow } from "@/types/waview";

interface InfoPanelProps {
  summary: ArchiveSummary;
  chat: ChatRow | null;
  avatarUrl?: string;
  avatarError?: string;
  onLoadAvatar: (chatId: number) => void;
}

export function InfoPanel({ summary, chat, avatarUrl, avatarError, onLoadAvatar }: InfoPanelProps) {
  return (
    <aside className="glass-card hidden h-full min-h-0 shrink-0 flex-col overflow-hidden rounded-2xl xl:flex">
      <div className="border-b border-border px-4 py-4">
        <div className="font-heading text-sm font-semibold text-foreground">{chat ? "Chat info" : "Archive info"}</div>
        <div className="mt-1 truncate text-xs text-muted-foreground">{summary.fileName}</div>
      </div>

      <div className="thin-scrollbar min-h-0 flex-1 overflow-auto p-4">
        {chat ? (
          <div className="flex flex-col items-center border-b border-border pb-5 text-center">
            <ChatAvatar
              chat={chat}
              avatarUrl={avatarUrl}
              avatarError={avatarError}
              onLoadAvatar={onLoadAvatar}
              size="lg"
            />
            <div className="mt-3 max-w-full truncate font-heading text-lg font-semibold text-foreground">{chat.title}</div>
            <div className="mt-1 text-sm text-muted-foreground">{chat.isGroup ? `${chat.memberCount || "Group"} members` : chat.jid}</div>
          </div>
        ) : null}

        <div className="mt-4 grid gap-2">
          <StatPill icon={MessageCircle} label="Messages" value={(chat?.messageCount ?? summary.counts.messages).toLocaleString()} />
          <StatPill icon={FileImage} label="Media" value={(chat?.mediaCount ?? summary.counts.media).toLocaleString()} />
          <StatPill icon={Star} label="Starred" value={(chat?.starredCount ?? summary.counts.starred).toLocaleString()} />
          {!chat ? <StatPill icon={Users} label="Groups" value={summary.counts.groups.toLocaleString()} /> : null}
          {!chat ? <StatPill icon={Phone} label="Calls" value={summary.counts.calls.toLocaleString()} /> : null}
          {!chat ? <StatPill icon={HardDrive} label="Size" value={formatBytes(summary.fileSize)} /> : null}
          {!chat ? <StatPill icon={Archive} label="Contacts" value={summary.counts.contacts.toLocaleString()} /> : null}
        </div>

        <div className="mt-4 rounded-2xl border border-border bg-background/70 p-4 text-sm shadow-sm">
          <div className="flex items-center gap-2 font-heading font-semibold text-foreground">
            <Archive className="h-4 w-4 text-primary" />
            Export
          </div>
          <dl className="mt-3 space-y-2 text-xs">
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Format</dt>
              <dd className="font-medium text-foreground">v{summary.formatVersion}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Created by</dt>
              <dd className="font-medium text-foreground">{summary.createdBy}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Export date</dt>
              <dd className="text-right font-medium text-foreground">
                {formatTimestamp(Date.parse(summary.exportInfo.export_date || ""), "Unknown")}
              </dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Embedded media</dt>
              <dd className="font-medium text-foreground">{summary.counts.downloadedMedia.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Reactions</dt>
              <dd className="font-medium text-foreground">{summary.counts.reactions.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Metadata</dt>
              <dd className="text-right font-medium text-foreground">
                {(
                  summary.counts.labels +
                  summary.counts.mentions +
                  summary.counts.vcards +
                  summary.counts.statuses +
                  summary.counts.messageEdits
                ).toLocaleString()}
              </dd>
            </div>
          </dl>
        </div>

        {summary.warnings.length ? (
          <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4">
            <div className="flex items-center gap-2 text-sm font-semibold text-amber-900">
              <AlertTriangle className="h-4 w-4" />
              Notes
            </div>
            <ul className="mt-2 space-y-2 text-xs leading-5 text-amber-800">
              {summary.warnings.map((warning) => (
                <li key={warning}>{warning}</li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    </aside>
  );
}
