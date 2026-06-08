import { useEffect, useMemo, useState, type ReactNode } from "react";

import { ThemeContext, type ThemeContextValue, type ThemeMode } from "@/context/theme-core";

function hexToRgb(hex: string) {
  const match = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return match ? `${parseInt(match[1], 16)} ${parseInt(match[2], 16)} ${parseInt(match[3], 16)}` : null;
}

function preferredTheme(): ThemeMode {
  if (typeof window === "undefined") return "light";
  const saved = safeStorageGet("wasensai-theme");
  if (saved === "dark" || saved === "light") return saved;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function safeStorageGet(key: string) {
  try {
    return typeof window === "undefined" ? null : window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeStorageSet(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Storage can be blocked in hardened browser profiles. Theme still works for the current session.
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<ThemeMode>(preferredTheme);
  const [glassMode, setGlassMode] = useState(() => safeStorageGet("wasensai-glass-mode") !== "false");
  const [accentColor, setAccentColor] = useState(() => safeStorageGet("wasensai-accent-color") || "#4F46E5");

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.remove("light", "dark");
    root.classList.add(theme);
    safeStorageSet("wasensai-theme", theme);
  }, [theme]);

  useEffect(() => {
    const root = window.document.documentElement;
    root.classList.toggle("glass-mode", glassMode);
    safeStorageSet("wasensai-glass-mode", String(glassMode));
  }, [glassMode]);

  useEffect(() => {
    const rgb = hexToRgb(accentColor);
    if (!rgb) return;
    const root = window.document.documentElement;
    root.style.setProperty("--primary", rgb);
    root.style.setProperty("--ring", rgb);
    safeStorageSet("wasensai-accent-color", accentColor);
  }, [accentColor]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      setTheme,
      toggleTheme: () => setTheme((current) => (current === "dark" ? "light" : "dark")),
      glassMode,
      setGlassMode,
      toggleGlassMode: () => setGlassMode((current) => !current),
      accentColor,
      setAccentColor,
    }),
    [accentColor, glassMode, theme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
