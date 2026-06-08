import { createContext, useContext } from "react";

export type ThemeMode = "light" | "dark";

export interface ThemeContextValue {
  theme: ThemeMode;
  setTheme: (theme: ThemeMode) => void;
  toggleTheme: () => void;
  glassMode: boolean;
  setGlassMode: (enabled: boolean) => void;
  toggleGlassMode: () => void;
  accentColor: string;
  setAccentColor: (color: string) => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used inside ThemeProvider");
  return context;
}

export const accentColors = [
  { name: "Indigo", value: "#4F46E5" },
  { name: "Blue", value: "#2563EB" },
  { name: "Purple", value: "#7C3AED" },
  { name: "Pink", value: "#DB2777" },
  { name: "Red", value: "#DC2626" },
  { name: "Orange", value: "#EA580C" },
  { name: "Green", value: "#16A34A" },
  { name: "Teal", value: "#0D9488" },
  { name: "Cyan", value: "#0891B2" },
];
