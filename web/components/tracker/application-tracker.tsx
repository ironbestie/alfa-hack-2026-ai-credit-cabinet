"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Loader2 } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { toast } from "sonner";
import { acceptOffer, getExplanation, getOffers, signOffer } from "@/api/applications";
import type { Application } from "@/api/types";
import { ErrorState } from "@/components/error-state";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useApplicationStatus } from "@/hooks/use-application-status";
import { formatMonths, formatRate, formatRub, formatTime } from "@/lib/format";
import { statusLabels, trackerStepIndex, trackerSteps } from "@/lib/statuses";
import { cn } from "@/lib/utils";

export function TrackerSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-40 w-full rounded-2xl" />
    </div>
  );
}

export function ApplicationTracker() {
  const { id } = useParams<{ id: string }>();
  const { application, timeline } = useApplicationStatus(id);

  if (application.isPending) return <TrackerSkeleton />;

  if (application.isError) {
    return <ErrorState title="Не удалось загрузить заявку" error={application.error} onRetry={() => application.refetch()} />;
  }

  const app = application.data;
  const current = trackerStepIndex(app.status);

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold">Заявка на {formatRub(app.requestedAmount)}</h1>
        <Badge variant={app.status === "REJECTED" ? "destructive" : "secondary"}>{statusLabels[app.status]}</Badge>
      </div>

      <ol className="grid gap-2 sm:grid-cols-6">
        {trackerSteps.map((step, i) => {
          const done = i < current;
          const active = i === current;
          return (
            <li key={step.key} className="flex items-center gap-3 sm:flex-col sm:items-start">
              <span
                className={cn(
                  "flex size-8 shrink-0 items-center justify-center rounded-full border text-xs font-semibold",
                  done && "border-success bg-success/15 text-success",
                  active && "border-primary bg-primary text-primary-foreground",
                  !done && !active && "border-border text-muted-foreground",
                )}
              >
                {done ? <Check className="size-4" /> : i + 1}
              </span>
              <span className={cn("text-sm", active ? "text-foreground" : "text-muted-foreground")}>{step.label}</span>
            </li>
          );
        })}
      </ol>

      <Decision app={app} />

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">История</h2>
        {timeline.isPending ? (
          <Skeleton className="h-24 w-full rounded-2xl" />
        ) : timeline.isError ? (
          <ErrorState error={timeline.error} onRetry={() => timeline.refetch()} />
        ) : (
          <ul className="space-y-2">
            {[...timeline.data].reverse().map((event) => (
              <li
                key={`${event.timestamp}-${event.eventType}`}
                className="flex gap-4 rounded-xl border border-border bg-card px-4 py-3 text-sm"
              >
                <span className="w-12 shrink-0 text-muted-foreground">{formatTime(event.timestamp)}</span>
                <span className="flex-1">{event.description}</span>
                <span className="text-muted-foreground">{event.actor}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function Decision({ app }: { app: Application }) {
  const queryClient = useQueryClient();
  const decided = ["APPROVED", "COUNTER_OFFER", "REJECTED"].includes(app.status);

  const explanation = useQuery({
    queryKey: ["application", app.id, "explanation"],
    queryFn: () => getExplanation(app.id),
    enabled: decided,
  });
  const offers = useQuery({
    queryKey: ["application", app.id, "offers"],
    queryFn: () => getOffers(app.id),
    enabled: decided,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["application", app.id] });
  const accept = useMutation({
    mutationFn: () => acceptOffer(app.id),
    onSuccess: refresh,
    onError: (error) => toast.error(error.message),
  });
  const sign = useMutation({
    mutationFn: () => signOffer(app.id),
    onSuccess: refresh,
    onError: (error) => toast.error(error.message),
  });

  if (["SUBMITTED", "ENRICHMENT", "SCORING", "MANUAL_REVIEW"].includes(app.status)) {
    return (
      <div className="flex items-center gap-3 rounded-2xl border border-border bg-card p-5">
        <Loader2 className="size-5 animate-spin text-primary" />
        <div>
          <p className="font-semibold">{statusLabels[app.status]}</p>
          <p className="text-sm text-muted-foreground">Обновим страницу сами, как только будет решение</p>
        </div>
      </div>
    );
  }

  if (app.status === "SIGNING") {
    return (
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-primary bg-card p-5">
        <div>
          <p className="font-semibold">Осталось подписать документы</p>
          <p className="text-sm text-muted-foreground">Подпись по коду из SMS</p>
        </div>
        <Button className="h-11 rounded-xl px-6" disabled={sign.isPending} onClick={() => sign.mutate()}>
          {sign.isPending && <Loader2 className="animate-spin" />}
          Подписать
        </Button>
      </div>
    );
  }

  if (app.status === "FUNDED") {
    return (
      <div className="rounded-2xl border border-success bg-success/10 p-5">
        <p className="font-semibold">Деньги отправлены на счёт</p>
        <p className="text-sm text-muted-foreground">Обычно они приходят в течение часа</p>
      </div>
    );
  }

  if (!decided) return null;

  return (
    <section className="space-y-4">
      {explanation.isPending ? (
        <Skeleton className="h-28 w-full rounded-2xl" />
      ) : explanation.isError ? (
        <ErrorState error={explanation.error} onRetry={() => explanation.refetch()} />
      ) : (
        <div className="space-y-3 rounded-2xl border border-border bg-card p-5">
          <p className="text-lg font-semibold">{explanation.data.summary}</p>
          {explanation.data.reasons.length > 0 && (
            <div>
              <p className="text-sm text-muted-foreground">Почему так</p>
              <ul className="list-disc pl-5 text-sm">
                {explanation.data.reasons.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            </div>
          )}
          {explanation.data.nextSteps.length > 0 && (
            <div>
              <p className="text-sm text-muted-foreground">Что можно сделать</p>
              <ul className="list-disc pl-5 text-sm">
                {explanation.data.nextSteps.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {offers.data && offers.data.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {offers.data.map((offer) => {
            const acceptable = offer.id === app.id;
            return (
              <div key={offer.id} className="space-y-3 rounded-2xl border border-border bg-card p-5">
                <p className="font-heading text-2xl font-bold">{formatRub(offer.amount)}</p>
                <p className="text-sm text-muted-foreground">
                  {formatMonths(offer.termMonths)} · ставка {formatRate(offer.interestRate)}
                  {offer.type === "STATE_GUARANTEE" && " · с поручительством Корпорации МСП"}
                </p>
                {acceptable ? (
                  <Button className="h-11 w-full rounded-xl" disabled={accept.isPending} onClick={() => accept.mutate()}>
                    {accept.isPending && <Loader2 className="animate-spin" />}
                    Принять
                  </Button>
                ) : (
                  <Link href="/apply" className={cn(buttonVariants({ variant: "outline" }), "h-11 w-full rounded-xl")}>
                    Оформить новую заявку
                  </Link>
                )}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
