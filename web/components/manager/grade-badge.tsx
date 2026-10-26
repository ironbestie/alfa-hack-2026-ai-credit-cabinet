import { cn } from "@/lib/utils";

const gradeClasses: Record<string, string> = {
  A: "bg-success/15 text-success",
  B: "bg-success/10 text-success",
  C: "bg-warning/15 text-warning",
  D: "bg-primary/15 text-primary",
  E: "bg-primary/25 text-primary",
};

export function GradeBadge({ grade }: { grade: string | null }) {
  if (!grade) return null;
  return (
    <span className={cn("rounded-md px-1.5 py-0.5 text-xs font-bold", gradeClasses[grade] ?? "bg-muted")}>{grade}</span>
  );
}
