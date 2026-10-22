import { AlertCircle } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

type ErrorStateProps = {
  title?: string;
  error: unknown;
  onRetry?: () => void;
};

export function ErrorState({ title = "Не удалось загрузить данные", error, onRetry }: ErrorStateProps) {
  const message = error instanceof Error ? error.message : "Попробуйте ещё раз чуть позже";

  return (
    <Alert variant="destructive">
      <AlertCircle />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription className="flex flex-wrap items-center gap-3">
        <span>{message}</span>
        {onRetry && (
          <Button size="sm" variant="outline" onClick={onRetry}>
            Повторить
          </Button>
        )}
      </AlertDescription>
    </Alert>
  );
}
