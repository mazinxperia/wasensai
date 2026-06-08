import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { Archive, FileImage, MessageCircle, Phone, Search, Settings, Star, Users, type LucideIcon } from "lucide-react";

import { CallsView } from "@/components/viewer/CallsView";
import { ChatSidebar } from "@/components/viewer/ChatSidebar";
import { ChatTimeline } from "@/components/viewer/ChatTimeline";
import { EmptyState } from "@/components/viewer/EmptyState";
import { InfoPanel } from "@/components/viewer/InfoPanel";
import { MediaPreviewDialog } from "@/components/viewer/MediaPreviewDialog";
import { ThemeControls } from "@/components/viewer/ThemeControls";
import { Button } from "@/components/ui/button";
import type { WaviewArchiveController } from "@/hooks/useWaviewArchive";
import type { ChatFilter } from "@/types/worker";
import type { ViewerMessage } from "@/types/waview";
import { cn } from "@/lib/utils";

interface ViewerShellProps {
  archive: WaviewArchiveController;
}

type ViewerSection = ChatFilter | "settings";

const navItems: Array<{ id: ViewerSection; label: string; icon: LucideIcon }> = [
  { id: "all", label: "Chats", icon: MessageCircle },
  { id: "groups", label: "Groups", icon: Users },
  { id: "media", label: "Media", icon: FileImage },
  { id: "starred", label: "Starred", icon: Star },
  { id: "calls", label: "Calls", icon: Phone },
  { id: "settings", label: "Settings", icon: Settings },
];

export function ViewerShell({ archive }: ViewerShellProps) {
  const [filter, setFilter] = useState<ViewerSection>("all");
  const [previewMessage, setPreviewMessage] = useState<ViewerMessage | null>(null);

  useEffect(() => {
    if (!archive.activeChatId && archive.chats.length) {
      archive.selectChat(archive.chats[0].id);
    }
  }, [archive]);

  if (!archive.summary) {
    return <EmptyState title="No archive loaded" body="Select a .waview file to begin." mode="archive" />;
  }

  const showCalls = filter === "calls";
  const showSettings = filter === "settings";
  const chatFilter: ChatFilter = filter === "settings" ? "all" : filter;
  const currentNav = navItems.find((item) => item.id === filter) || navItems[0];
  const CurrentIcon = currentNav.icon;

  return (
    <main className="grain relative h-screen overflow-hidden bg-background text-foreground">
      <div className="absolute inset-0 z-0 bg-gradient-to-br from-primary/10 via-background to-cyan-500/10" />

      <aside className="glass-sidebar fixed left-0 top-0 z-40 flex h-screen w-20 flex-col border-r xl:w-64">
        <div className="flex h-16 items-center justify-center gap-3 border-b border-border px-3 xl:justify-start xl:px-4">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-primary text-primary-foreground">
            <Archive className="h-5 w-5" />
          </div>
          <div className="hidden min-w-0 xl:block">
            <div className="truncate font-heading text-lg font-semibold">WA Sensai</div>
            <div className="truncate text-xs text-muted-foreground">{archive.summary.fileName}</div>
          </div>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = item.id === filter;
            const badge =
              item.id === "all"
                ? archive.summary?.counts.chats
                : item.id === "groups"
                  ? archive.summary?.counts.groups
                : item.id === "media"
                  ? archive.summary?.counts.media
                  : item.id === "starred"
                    ? archive.summary?.counts.starred
                    : item.id === "calls"
                      ? archive.summary?.counts.calls
                      : undefined;

            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setFilter(item.id)}
                title={item.label}
                className={cn(
                  "flex w-full items-center justify-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium transition-all duration-200 hover:bg-primary/15 xl:justify-start",
                  active && "bg-primary/20 font-semibold text-primary",
                )}
              >
                <Icon className={cn("h-5 w-5 shrink-0", active ? "text-primary" : "text-muted-foreground")} />
                <span className="hidden flex-1 xl:block">{item.label}</span>
                {badge !== undefined ? <span className="hidden text-xs text-muted-foreground xl:inline">{badge.toLocaleString()}</span> : null}
                {active ? <span className="hidden h-1.5 w-1.5 rounded-full bg-primary xl:inline-block" /> : null}
              </button>
            );
          })}
        </nav>

        <div className="border-t border-border p-3">
          <Button variant="ghost" className="w-full justify-center xl:justify-start" onClick={archive.closeArchive} title="Close archive">
            <Archive className="h-4 w-4 xl:hidden" />
            <span className="hidden xl:inline">Close archive</span>
          </Button>
        </div>
      </aside>

      <header className="fixed left-20 right-0 top-0 z-30 flex h-16 items-center gap-4 border-b border-border bg-background/80 px-4 backdrop-blur-lg xl:left-64 xl:px-6">
        <div className="grid h-10 w-10 place-items-center rounded-xl bg-primary/10 text-primary">
          <CurrentIcon className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <div className="font-heading text-lg font-semibold">{currentNav.label}</div>
          <div className="truncate text-xs text-muted-foreground">
            {showCalls ? `${archive.calls.length.toLocaleString()} call records` : `${archive.chats.length.toLocaleString()} visible chats`}
          </div>
        </div>
        <div className="relative ml-4 hidden max-w-xl flex-1 lg:block">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            readOnly
            value={archive.activeChat?.title || archive.summary.fileName}
            className="h-10 w-full rounded-xl border border-transparent bg-secondary/50 pl-10 pr-4 text-sm text-muted-foreground outline-none"
          />
        </div>
        <div className="ml-auto">
          <ThemeControls compact />
        </div>
      </header>

      <section className="relative z-10 ml-20 h-screen overflow-hidden pt-16 xl:ml-64">
        <motion.div
          key={filter}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.22 }}
          className={cn(
            "grid h-full gap-4 p-4",
            showCalls || showSettings
              ? "grid-cols-1"
              : "grid-cols-[320px_minmax(0,1fr)] xl:grid-cols-[360px_minmax(0,1fr)] 2xl:grid-cols-[390px_minmax(0,1fr)_320px]",
          )}
        >
          {showSettings ? (
            <SettingsPanel archive={archive} />
          ) : showCalls ? (
            <CallsView calls={archive.calls} />
          ) : (
            <>
              <div className="min-h-0">
                <ChatSidebar
                  summary={archive.summary}
                  chats={archive.chats}
                  activeChatId={archive.activeChatId}
                  filter={chatFilter}
                  avatarUrls={archive.avatarUrls}
                  avatarErrors={archive.avatarErrors}
                  onFilterChange={(value) => setFilter(value)}
                  onSelectChat={archive.selectChat}
                  onLoadAvatar={archive.loadAvatar}
                  onCloseArchive={archive.closeArchive}
                />
              </div>

              <div className="min-h-0">
                <ChatTimeline
                  chat={archive.activeChat}
                  messages={archive.activeMessages}
                  avatarUrl={archive.activeChat ? archive.avatarUrls[archive.activeChat.id] : undefined}
                  avatarError={archive.activeChat ? archive.avatarErrors[archive.activeChat.id] : undefined}
                  mediaUrls={archive.mediaUrls}
                  mediaErrors={archive.mediaErrors}
                  onLoadAvatar={archive.loadAvatar}
                  onEnsureMedia={archive.loadMedia}
                  onOpenMedia={(message) => {
                    setPreviewMessage(message);
                    if (message.media?.status === "downloaded") archive.loadMedia(message.id);
                  }}
                />
              </div>

              <InfoPanel
                summary={archive.summary}
                chat={archive.activeChat}
                avatarUrl={archive.activeChat ? archive.avatarUrls[archive.activeChat.id] : undefined}
                avatarError={archive.activeChat ? archive.avatarErrors[archive.activeChat.id] : undefined}
                onLoadAvatar={archive.loadAvatar}
              />
            </>
          )}
        </motion.div>
      </section>

      <MediaPreviewDialog
        message={previewMessage}
        media={previewMessage ? archive.mediaUrls[previewMessage.id] : undefined}
        mediaError={previewMessage ? archive.mediaErrors[previewMessage.id] : undefined}
        onLoadMedia={archive.loadMedia}
        onOpenChange={(open) => {
          if (!open) setPreviewMessage(null);
        }}
      />
    </main>
  );
}

function SettingsPanel({ archive }: { archive: WaviewArchiveController }) {
  return (
    <div className="page-fade-in h-full overflow-auto rounded-2xl border border-border bg-card p-6 shadow-sm">
      <div className="mb-6">
        <h2 className="font-heading text-2xl font-semibold tracking-tight">Personalization</h2>
        <p className="mt-1 text-sm text-muted-foreground">Theme, glass mode and accent color follow the AssetFlow style.</p>
      </div>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="glass-card rounded-2xl p-5">
          <div className="font-heading text-lg font-semibold">Appearance</div>
          <p className="mt-1 text-sm text-muted-foreground">Changes are saved in this browser.</p>
          <div className="mt-5">
            <ThemeControls />
          </div>
        </div>
        <div className="glass-card rounded-2xl p-5">
          <div className="font-heading text-lg font-semibold">Archive</div>
          <dl className="mt-4 space-y-3 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Chats</dt>
              <dd className="font-semibold">{archive.summary?.counts.chats.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Messages</dt>
              <dd className="font-semibold">{archive.summary?.counts.messages.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Embedded media</dt>
              <dd className="font-semibold">{archive.summary?.counts.downloadedMedia.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Calls</dt>
              <dd className="font-semibold">{archive.summary?.counts.calls.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted-foreground">Metadata rows</dt>
              <dd className="font-semibold">
                {(
                  (archive.summary?.counts.labels || 0) +
                  (archive.summary?.counts.mentions || 0) +
                  (archive.summary?.counts.vcards || 0) +
                  (archive.summary?.counts.statuses || 0) +
                  (archive.summary?.counts.messageEdits || 0)
                ).toLocaleString()}
              </dd>
            </div>
          </dl>
        </div>
      </div>
    </div>
  );
}
