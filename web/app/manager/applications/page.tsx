"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { listApplications } from "@/api/applications";
import { ErrorState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate, formatRub } from "@/lib/format";

export default function ManagerApplicationsPage() {
  const [page, setPage] = useState(0);
  const applications = useQuery({
    queryKey: ["manager", "applications", page],
    queryFn: () => listApplications({ page, size: 20 }),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
  });

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold">
        Все <span className="font-accent font-medium text-primary italic">заявки</span>
      </h1>
      {applications.isPending ? (
        <Skeleton className="h-64 w-full rounded-2xl" />
      ) : applications.isError ? (
        <ErrorState error={applications.error} onRetry={() => applications.refetch()} />
      ) : applications.data.items.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-border p-10 text-center text-muted-foreground">Заявок пока нет</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-border">
            <table className="w-full min-w-[720px] text-sm">
              <thead className="bg-card text-left text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Компания</th>
                  <th className="px-4 py-3 font-medium">Продукт</th>
                  <th className="px-4 py-3 font-medium">Сумма</th>
                  <th className="px-4 py-3 font-medium">Оценка</th>
                  <th className="px-4 py-3 font-medium">Статус</th>
                  <th className="px-4 py-3 font-medium">Обновлена</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {applications.data.items.map((app) => (
                  <tr key={app.id} className="transition-colors hover:bg-accent">
                    <td className="px-4 py-3">
                      <Link href={`/manager/applications/${app.id}`} className="font-medium hover:underline">
                        {app.company.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{app.product.name}</td>
                    <td className="px-4 py-3 font-medium">{formatRub(app.requestedAmount)}</td>
                    <td className="px-4 py-3">{app.riskScore ?? "—"}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={app.status} />
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{formatDate(app.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={applications.data} onChange={setPage} />
        </>
      )}
    </div>
  );
}
