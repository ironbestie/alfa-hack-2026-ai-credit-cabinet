"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { listApplications } from "@/api/applications";
import { ErrorState } from "@/components/error-state";
import { Pager } from "@/components/pager";
import { StatusBadge } from "@/components/status-badge";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate, formatMonths, formatRub } from "@/lib/format";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 10;

export default function ApplicationsPage() {
  const [page, setPage] = useState(0);
  const applications = useQuery({
    queryKey: ["applications", page],
    queryFn: () => listApplications({ page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-bold">
          Мои <span className="font-accent font-medium text-primary italic">заявки</span>
        </h1>
        <Link href="/apply" className={cn(buttonVariants(), "h-10 rounded-xl px-5")}>
          Новая заявка
        </Link>
      </div>
      {applications.isPending ? (
        <div className="space-y-3">
          <Skeleton className="h-20 w-full rounded-2xl" />
          <Skeleton className="h-20 w-full rounded-2xl" />
        </div>
      ) : applications.isError ? (
        <ErrorState error={applications.error} onRetry={() => applications.refetch()} />
      ) : applications.data.items.length === 0 ? (
        <div className="space-y-4 rounded-2xl border border-dashed border-border p-8 text-center">
          <p className="text-muted-foreground">Заявок пока нет</p>
          <Link href="/apply" className={cn(buttonVariants(), "h-11 rounded-xl px-6")}>
            Оформить заявку
          </Link>
        </div>
      ) : (
        <>
          <ul className="space-y-3">
            {applications.data.items.map((app) => (
              <li key={app.id}>
                <Link
                  href={`/applications/${app.id}`}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card p-5 transition-colors hover:bg-accent"
                >
                  <span className="space-y-1">
                    <span className="block font-heading text-lg font-semibold">{formatRub(app.requestedAmount)}</span>
                    <span className="block text-sm text-muted-foreground">
                      {app.company.name} · {app.product.name} · {formatMonths(app.requestedTermMonths)}
                    </span>
                  </span>
                  <span className="flex flex-col items-end gap-1">
                    <StatusBadge status={app.status} />
                    <span className="text-xs text-muted-foreground">обновлена {formatDate(app.updatedAt)}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          <Pager page={applications.data} onChange={setPage} />
        </>
      )}
    </div>
  );
}
