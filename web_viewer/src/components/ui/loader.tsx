import { cn } from "@/lib/utils";

type LoaderVariant = "circular" | "dots" | "typing" | "text-shimmer";

interface LoaderProps {
  variant?: LoaderVariant;
  size?: "sm" | "md" | "lg";
  text?: string;
  className?: string;
}

export function Loader({ variant = "circular", size = "md", text = "Loading", className }: LoaderProps) {
  if (variant === "dots" || variant === "typing") {
    const dotSize = size === "lg" ? "h-2.5 w-2.5" : size === "sm" ? "h-1.5 w-1.5" : "h-2 w-2";
    return (
      <div className={cn("flex items-center gap-1", className)}>
        {[0, 1, 2].map((dot) => (
          <span
            key={dot}
            className={cn("rounded-full bg-primary animate-bounce-dots", dotSize)}
            style={{ animationDelay: `${dot * 160}ms` }}
          />
        ))}
        <span className="sr-only">{text}</span>
      </div>
    );
  }

  if (variant === "text-shimmer") {
    return (
      <span
        className={cn(
          "bg-[linear-gradient(90deg,rgb(var(--muted-foreground))_0%,rgb(var(--foreground))_50%,rgb(var(--muted-foreground))_100%)] bg-[length:200%_100%] bg-clip-text text-sm font-medium text-transparent animate-shimmer",
          className,
        )}
      >
        {text}
      </span>
    );
  }

  const sizeClass = size === "lg" ? "h-6 w-6" : size === "sm" ? "h-4 w-4" : "h-5 w-5";
  return <span className={cn("inline-block rounded-full border-2 border-primary border-t-transparent animate-spin", sizeClass, className)} />;
}
