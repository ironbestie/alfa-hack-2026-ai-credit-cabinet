import type { Score } from "@/api/types";
import { cn } from "@/lib/utils";

const riskLabels: Record<string, string> = {
  low: "низкий риск",
  medium: "средний риск",
  high: "высокий риск",
};

export function ScoreFactors({ score }: { score: Score }) {
  const max = Math.max(...score.factors.map((f) => Math.abs(f.contribution)), 1);

  return (
    <div className="space-y-4 rounded-2xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <h3 className="text-lg font-semibold">Из чего сложилась оценка</h3>
        <span className="text-sm text-muted-foreground">
          {score.score} баллов · грейд {score.grade} · {riskLabels[score.riskLevel] ?? score.riskLevel}
        </span>
      </div>
      <ul className="space-y-3">
        {score.factors.map((factor) => {
          const positive = factor.contribution >= 0;
          return (
            <li key={`${factor.name}-${factor.description}`} className="grid gap-1 text-sm sm:grid-cols-[1fr_auto]">
              <div className="flex flex-wrap justify-between gap-2">
                <span>{factor.name}</span>
                <span className="text-muted-foreground">{factor.description}</span>
              </div>
              <span className={cn("font-semibold tabular-nums sm:w-14 sm:text-right", positive ? "text-success" : "text-primary")}>
                {positive ? "+" : ""}
                {factor.contribution}
              </span>
              <div className="h-1.5 rounded-full bg-muted sm:col-span-2">
                <div
                  className={cn("h-full rounded-full", positive ? "bg-success" : "bg-primary")}
                  style={{ width: `${(Math.abs(factor.contribution) / max) * 100}%` }}
                />
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
