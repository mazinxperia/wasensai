import { Check, Moon, Palette, Sparkles, Sun } from "lucide-react";

import { Button } from "@/components/ui/button";
import { SwitchButton } from "@/components/viewer/SwitchButton";
import { accentColors, useTheme } from "@/context/theme-core";
import { cn } from "@/lib/utils";

interface ThemeControlsProps {
  compact?: boolean;
}

export function ThemeControls({ compact = false }: ThemeControlsProps) {
  const { theme, toggleTheme, glassMode, toggleGlassMode, accentColor, setAccentColor } = useTheme();

  return (
    <div className={cn("flex items-center gap-2", !compact && "flex-wrap")}>
      <Button variant="ghost" size="icon" onClick={toggleTheme} title="Dark mode" data-testid="theme-toggle">
        {theme === "dark" ? <Moon className="h-5 w-5" /> : <Sun className="h-5 w-5" />}
      </Button>

      <SwitchButton checked={glassMode} onCheckedChange={toggleGlassMode} icon={Sparkles} label={compact ? "" : "Glass"} />

      <div className="flex items-center gap-1 rounded-xl border border-border bg-background/70 p-1 shadow-sm backdrop-blur">
        <Palette className="ml-1 h-3.5 w-3.5 text-muted-foreground" />
        {accentColors.slice(0, compact ? 5 : accentColors.length).map((color) => (
          <button
            key={color.value}
            type="button"
            onClick={() => setAccentColor(color.value)}
            title={color.name}
            className="relative h-6 w-6 rounded-lg transition-transform hover:scale-110 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 focus:ring-offset-background"
            style={{ backgroundColor: color.value }}
          >
            {accentColor.toLowerCase() === color.value.toLowerCase() ? (
              <Check className="absolute inset-0 m-auto h-3.5 w-3.5 text-white drop-shadow-md" />
            ) : null}
          </button>
        ))}
      </div>
    </div>
  );
}
