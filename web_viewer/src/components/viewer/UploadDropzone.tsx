import { useRef, useState } from "react";
import { motion } from "motion/react";
import {
  Archive,
  Database,
  FileCheck2,
  FileImage,
  HardDrive,
  MessageCircle,
  Palette,
  Phone,
  Settings,
  ShieldCheck,
  UploadCloud,
  XCircle,
  type LucideIcon,
} from "lucide-react";

import { ThemeControls } from "@/components/viewer/ThemeControls";
import { Button } from "@/components/ui/button";
import { Loader } from "@/components/ui/loader";
import { Progress } from "@/components/ui/progress";
import { formatBytes } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { WorkerProgress } from "@/types/worker";

interface UploadDropzoneProps {
  status: "idle" | "loading" | "ready" | "error";
  progress: WorkerProgress;
  error: string;
  onOpenFile: (file: File) => void;
}

const navItems: Array<{ label: string; icon: LucideIcon; active?: boolean }> = [
  { label: "Open", icon: Archive, active: true },
  { label: "Chats", icon: MessageCircle },
  { label: "Media", icon: FileImage },
  { label: "Calls", icon: Phone },
  { label: "Settings", icon: Settings },
];

const archiveFacts: Array<{ label: string; value: string; icon: LucideIcon }> = [
  { label: "Local read", value: "Browser session", icon: ShieldCheck },
  { label: "Archive mode", value: "ZIP64 ready", icon: Database },
  { label: "Media", value: "On demand", icon: HardDrive },
];

function validateFile(file: File) {
  if (!file.name.toLowerCase().endsWith(".waview")) {
    return "Select a WA Sensai .waview archive.";
  }
  return "";
}

export function UploadDropzone({ status, progress, error, onOpenFile }: UploadDropzoneProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [localError, setLocalError] = useState("");

  const chooseFile = (file?: File) => {
    if (!file) return;
    const validation = validateFile(file);
    setSelectedFile(file);
    setLocalError(validation);
    if (!validation) onOpenFile(file);
  };

  const displayedError = localError || error;
  const isLoading = status === "loading";

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
            <div className="truncate text-xs text-muted-foreground">Web viewer</div>
          </div>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {navItems.map(({ label, icon: Icon, active }) => (
            <button
              key={label}
              type="button"
              disabled={!active}
              title={label}
              className={cn(
                "flex w-full items-center justify-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium transition-all duration-200 xl:justify-start",
                active ? "bg-primary/20 text-primary" : "cursor-default text-muted-foreground/50",
              )}
            >
              <Icon className="h-5 w-5 shrink-0" />
              <span className="hidden flex-1 xl:block">{label}</span>
              {active ? <span className="hidden h-1.5 w-1.5 rounded-full bg-primary xl:inline-block" /> : null}
            </button>
          ))}
        </nav>

        <div className="border-t border-border p-3">
          <div className="hidden items-center gap-2 rounded-lg bg-primary/10 px-3 py-2 text-xs font-medium text-primary xl:flex">
            <Palette className="h-4 w-4" />
            Theme ready
          </div>
          <div className="grid h-10 w-full place-items-center rounded-lg bg-primary/10 text-primary xl:hidden">
            <Palette className="h-4 w-4" />
          </div>
        </div>
      </aside>

      <header className="fixed left-20 right-0 top-0 z-30 flex h-16 items-center gap-4 border-b border-border bg-background/80 px-4 backdrop-blur-lg xl:left-64 xl:px-6">
        <div className="grid h-10 w-10 place-items-center rounded-xl bg-primary/10 text-primary">
          <UploadCloud className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <div className="font-heading text-lg font-semibold">Open archive</div>
          <div className="truncate text-xs text-muted-foreground">
            {selectedFile ? selectedFile.name : "No .waview selected"}
          </div>
        </div>
        <div className="ml-auto">
          <ThemeControls compact />
        </div>
      </header>

      <section className="relative z-10 ml-20 h-screen overflow-auto pt-16 xl:ml-64">
        <div className="mx-auto grid min-h-full w-full max-w-6xl gap-4 p-4 lg:grid-cols-[minmax(0,1fr)_360px]">
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.24, ease: "easeOut" }}
            className="glass-card flex min-h-[520px] flex-col overflow-hidden rounded-xl"
          >
            <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-4">
              <div>
                <h1 className="font-heading text-xl font-semibold tracking-tight">Archive picker</h1>
                <p className="mt-1 text-sm text-muted-foreground">Select a WA Sensai .waview backup.</p>
              </div>
              {isLoading ? <Loader variant="dots" /> : null}
            </div>

            <div className="flex flex-1 flex-col p-5">
              <input
                ref={inputRef}
                type="file"
                accept=".waview,application/zip,application/octet-stream"
                className="hidden"
                onChange={(event) => chooseFile(event.target.files?.[0])}
              />
              <button
                type="button"
                onClick={() => inputRef.current?.click()}
                onDrop={(event) => {
                  event.preventDefault();
                  setDragOver(false);
                  chooseFile(event.dataTransfer.files?.[0]);
                }}
                onDragOver={(event) => {
                  event.preventDefault();
                  setDragOver(true);
                }}
                onDragLeave={() => setDragOver(false)}
                className={cn(
                  "group relative flex flex-1 flex-col items-center justify-center overflow-hidden rounded-xl border border-dashed p-6 text-center transition-all duration-300",
                  dragOver
                    ? "border-primary bg-primary/10 shadow-[0_0_0_6px_rgb(var(--primary)/0.08)]"
                    : "border-border bg-secondary/35 hover:border-primary/60 hover:bg-card/80",
                )}
              >
                <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-primary via-cyan-500 to-fuchsia-500 opacity-80" />
                <motion.div
                  animate={{ y: dragOver ? -4 : 0, scale: dragOver ? 1.04 : 1 }}
                  className="grid h-16 w-16 place-items-center rounded-xl bg-primary text-primary-foreground shadow-soft"
                >
                  <UploadCloud className="h-8 w-8" />
                </motion.div>
                <div className="mt-5 max-w-sm">
                  <div className="font-heading text-2xl font-semibold tracking-tight">
                    {dragOver ? "Drop .waview here" : "Choose .waview file"}
                  </div>
                  <p className="mt-2 text-sm leading-6 text-muted-foreground">
                    Large backups stay on this machine and are indexed in the browser.
                  </p>
                </div>
                <div className="mt-5">
                  <Button type="button" variant="whatsapp" size="lg">
                    <HardDrive className="h-4 w-4" />
                    Browse local file
                  </Button>
                </div>
              </button>
            </div>
          </motion.div>

          <motion.aside
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.24, ease: "easeOut", delay: 0.04 }}
            className="space-y-4"
          >
            <div className="glass-card rounded-xl p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="font-heading text-base font-semibold">File status</div>
                  <div className="mt-1 text-xs text-muted-foreground">{isLoading ? progress.phase : "Waiting for archive"}</div>
                </div>
                <div className="grid h-10 w-10 place-items-center rounded-lg bg-primary/10 text-primary">
                  <FileCheck2 className="h-5 w-5" />
                </div>
              </div>

              <div className="mt-4 rounded-lg border border-border bg-secondary/35 p-3">
                {selectedFile ? (
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-foreground">{selectedFile.name}</div>
                    <div className="mt-1 text-xs text-muted-foreground">{formatBytes(selectedFile.size)}</div>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <HardDrive className="h-4 w-4 text-primary" />
                    No archive selected
                  </div>
                )}
              </div>

              {isLoading ? (
                <div className="mt-4 space-y-2">
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>{progress.detail}</span>
                    <span>{Math.round(progress.progress)}%</span>
                  </div>
                  <Progress value={progress.progress} />
                </div>
              ) : null}

              {displayedError ? (
                <div className="mt-4 flex items-start gap-2 rounded-lg bg-red-50 px-3 py-2 text-left text-sm text-red-700 dark:bg-red-950/50 dark:text-red-200">
                  <XCircle className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>{displayedError}</span>
                </div>
              ) : null}
            </div>

            <div className="grid gap-3">
              {archiveFacts.map(({ label, value, icon: Icon }) => (
                <div key={label} className="glass-card flex items-center gap-3 rounded-xl p-4">
                  <div className="grid h-10 w-10 place-items-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="h-5 w-5" />
                  </div>
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-foreground">{label}</div>
                    <div className="mt-1 truncate text-xs text-muted-foreground">{value}</div>
                  </div>
                </div>
              ))}
            </div>
          </motion.aside>
        </div>
      </section>
    </main>
  );
}
