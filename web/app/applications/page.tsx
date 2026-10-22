"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { listApplications } from "@/api/applications";
import { ErrorState } from "@/components/error-state";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMonths, formatRub } from "@/lib/format";
import { statusLabels } from "@/lib/statuses";
import { cn } from "@/lib/utils";

export default function ApplicationsPage() {
  const applications = useQuery({ queryKey: ["applications"], queryFn: listApplications });

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold">
        Мои <span className="font-accent font-medium text-primary italic">заявки</span>
      </h1>
      {applications.isPending ? (
        <div className="space-y-3">
          <Skeleton className="h-20 w-full rounded-2xl" />
          <Skeleton className="h-20 w-full rounded-2xl" />
        </div>
      ) : applications.isError ? (
        <ErrorState error={applications.error} onRetry={() => applications.refetch()} />
      ) : applications.data.length === 0 ? (
        <div className="space-y-4 rounded-2xl border border-dashed border-border p-8 text-center">
          <p className="text-muted-foreground">Заявок пока нет</p>
          <Link href="/apply" className={cn(buttonVariants(), "h-11 rounded-xl px-6")}>
            Оформить заявку
          </Link>
        </div>
      ) : (
        <ul className="space-y-3">
          {applications.data.map((app) => (
            <li key={app.id}>
              <Link
                href={`/applications/${app.id}`}
                className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card p-5 transition-colors hover:bg-accent"
              >
                <span>
                  <span className="block font-semibold">{formatRub(app.requestedAmount)}</span>
                  <span className="text-sm text-muted-foreground">{formatMonths(app.requestedTermMonths)}</span>
                </span>
                <Badge variant="secondary">{statusLabels[app.status]}</Badge>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
