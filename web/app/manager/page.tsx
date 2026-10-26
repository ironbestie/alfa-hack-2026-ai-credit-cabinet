"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { getPendingApplications } from "@/api/manager";
import { ErrorState } from "@/components/error-state";
import { GradeBadge } from "@/components/manager/grade-badge";
import { Pager } from "@/components/pager";
import { Skeleton } from "@/components/ui/skeleton";
import { formatRub, waitingFor } from "@/lib/format";
import { cn } from "@/lib/utils";

const OVERDUE_MINUTES = 15;

export default function ManagerQueuePage() {
  const [page, setPage] = useState(0);
  const pending = useQuery({
    queryKey: ["manager", "pending", page],
    queryFn: () => getPendingApplications({ page, size: 20 }),
    placeholderData: keepPreviousData,
    refetchInterval: 10_000,
  });

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div className="space-y-1">
        <h1 className="text-3xl font-bold">
          Очередь <span className="font-accent font-medium text-primary italic">на проверку</span>
        </h1>
        <p className="text-muted-foreground">
          Заявки, которые система не смогла решить сама. Сверху те, что ждут дольше всего.
        </p>
      </div>

      {pending.isPending ? (
        <div className="space-y-2">
          <Skeleton className="h-16 w-full rounded-xl" />
          <Skeleton className="h-16 w-full rounded-xl" />
        </div>
      ) : pending.isError ? (
        <ErrorState error={pending.error} onRetry={() => pending.refetch()} />
      ) : pending.data.items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border p-10 text-center">
          <p className="font-hand text-3xl text-muted-foreground">очередь пуста</p>
          <p className="mt-2 text-sm text-muted-foreground">Все заявки обработаны. Новые появятся здесь автоматически.</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-border">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="bg-card text-left text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Компания</th>
                  <th className="px-4 py-3 font-medium">Сумма</th>
                  <th className="px-4 py-3 font-medium">Оценка</th>
                  <th className="px-4 py-3 font-medium">Причина</th>
                  <th className="px-4 py-3 font-medium">Ждёт</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {[...pending.data.items]
                  .sort((a, b) => a.updatedAt.localeCompare(b.updatedAt))
                  .map((item) => {
                    const minutes = (pending.dataUpdatedAt - new Date(item.updatedAt).getTime()) / 60_000;
                    return (
                      <tr key={item.id} className="transition-colors hover:bg-accent">
                        <td className="px-4 py-3">
                          <Link href={`/manager/applications/${item.id}`} className="block font-medium hover:underline">
                            {item.company.name}
                          </Link>
                          <span className="text-xs text-muted-foreground">ИНН {item.company.inn}</span>
                        </td>
                        <td className="px-4 py-3 font-medium">{formatRub(item.amount)}</td>
                        <td className="px-4 py-3">
                          {item.score !== null ? (
                            <span className="flex items-center gap-2">
                              {item.score} <GradeBadge grade={item.grade} />
                            </span>
                          ) : (
                            <span className="text-muted-foreground">нет оценки</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{item.reason ?? "—"}</td>
                        <td className={cn("px-4 py-3 whitespace-nowrap", minutes > OVERDUE_MINUTES && "font-semibold text-primary")}>
                          {waitingFor(item.updatedAt, pending.dataUpdatedAt)}
                        </td>
                      </tr>
                    );
                  })}
              </tbody>
            </table>
          </div>
          <Pager page={pending.data} onChange={setPage} />
        </>
      )}
    </div>
  );
}
