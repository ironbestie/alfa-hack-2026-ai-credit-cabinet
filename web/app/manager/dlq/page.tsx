"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, RotateCcw } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { getDeadLetters, retryDeadLetter } from "@/api/manager";
import { ErrorState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate, formatTime } from "@/lib/format";

export default function DeadLettersPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const deadLetters = useQuery({
    queryKey: ["manager", "dlq", page],
    queryFn: () => getDeadLetters({ page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  const retry = useMutation({
    mutationFn: retryDeadLetter,
    onSuccess: () => {
      toast.success("Заявка вернулась в обработку, система подхватит её в течение 30 секунд");
      queryClient.invalidateQueries({ queryKey: ["manager", "dlq"] });
    },
    onError: (error) => toast.error(error.message),
  });

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10 sm:px-6">
      <div className="space-y-1">
        <h1 className="text-3xl font-bold">
          Сбои <span className="font-accent font-medium text-primary italic">обработки</span>
        </h1>
        <p className="text-muted-foreground">
          Заявки, которые не удалось обработать после всех автоматических повторов. Их можно отправить на обработку
          ещё раз.
        </p>
      </div>

      {deadLetters.isPending ? (
        <Skeleton className="h-40 w-full rounded-2xl" />
      ) : deadLetters.isError ? (
        <ErrorState error={deadLetters.error} onRetry={() => deadLetters.refetch()} />
      ) : deadLetters.data.items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border p-10 text-center">
          <p className="font-hand text-3xl text-muted-foreground">всё работает</p>
          <p className="mt-2 text-sm text-muted-foreground">Сбоев обработки нет.</p>
        </div>
      ) : (
        <>
          <ul className="space-y-3">
            {deadLetters.data.items.map((item) => (
              <li
                key={item.applicationId}
                className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-border bg-card p-5"
              >
                <div className="min-w-0 space-y-1">
                  <Link href={`/manager/applications/${item.applicationId}`} className="font-medium hover:underline">
                    Заявка {item.applicationId.slice(0, 8)}
                  </Link>
                  <p className="truncate text-sm text-muted-foreground">{item.lastError ?? "Причина не указана"}</p>
                  <p className="text-xs text-muted-foreground">
                    Попыток: {item.attempts} · последняя {formatDate(item.updatedAt)} в {formatTime(item.updatedAt)}
                  </p>
                </div>
                <Button
                  variant="outline"
                  disabled={retry.isPending}
                  onClick={() => retry.mutate(item.applicationId)}
                >
                  {retry.isPending && retry.variables === item.applicationId ? (
                    <Loader2 className="animate-spin" />
                  ) : (
                    <RotateCcw />
                  )}
                  Повторить
                </Button>
              </li>
            ))}
          </ul>
          <Pager page={deadLetters.data} onChange={setPage} />
        </>
      )}
    </div>
  );
}
