import type { ApplicationStatus } from "@/api/types";
import { statusLabels, statusTone, type StatusTone } from "@/lib/statuses";
import { cn } from "@/lib/utils";

const toneClasses: Record<StatusTone, string> = {
  progress: "border-border bg-secondary text-foreground",
  action: "border-primary/40 bg-primary/15 text-primary",
  success: "border-success/40 bg-success/15 text-success",
  danger: "border-destructive/40 bg-destructive/15 text-destructive",
  muted: "border-border bg-transparent text-muted-foreground",
};

export function StatusBadge({ status, className }: { status: ApplicationStatus; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium whitespace-nowrap",
        toneClasses[statusTone[status]],
        className,
      )}
    >
      {statusLabels[status]}
    </span>
  );
}
