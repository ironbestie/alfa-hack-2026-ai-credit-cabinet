"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Loader2, PartyPopper } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { toast } from "sonner";
import { acceptOffer, getExplanation, getOffers, getScore, signOffer } from "@/api/applications";
import type { Application } from "@/api/types";
import { DocumentsPanel } from "@/components/documents-panel";
import { ErrorState } from "@/components/error-state";
import { ScoreFactors } from "@/components/score-factors";
import { StatusBadge } from "@/components/status-badge";
import { Timeline } from "@/components/timeline";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useApplicationStatus } from "@/hooks/use-application-status";
import { formatMonths, formatRate, formatRub } from "@/lib/format";
import { decidedStatuses, offerTypeLabels, processingStatuses, statusLabels, trackerStepIndex, trackerSteps } from "@/lib/statuses";
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
      <div className="space-y-2">
        <p className="text-sm text-muted-foreground">
          {app.company.name} · {app.product.name}
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-bold">Заявка на {formatRub(app.requestedAmount)}</h1>
          <StatusBadge status={app.status} />
        </div>
      </div>

      <ol className="grid gap-3 sm:grid-cols-6">
        {trackerSteps.map((step, i) => {
          const done = i < current || app.status === "FUNDED";
          const active = i === current && app.status !== "FUNDED";
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

      {(app.status === "NEEDS_MORE_INFO" || app.status === "MANUAL_REVIEW") && (
        <DocumentsPanel applicationId={app.id} canUpload={app.status === "NEEDS_MORE_INFO"} />
      )}

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">История</h2>
        {timeline.isPending ? (
          <Skeleton className="h-24 w-full rounded-2xl" />
        ) : timeline.isError ? (
          <ErrorState error={timeline.error} onRetry={() => timeline.refetch()} />
        ) : (
          <Timeline events={timeline.data.items} />
        )}
      </section>
    </div>
  );
}

function Decision({ app }: { app: Application }) {
  const queryClient = useQueryClient();
  const decided = decidedStatuses.includes(app.status);
  const scored = app.riskScore !== null;

  const explanation = useQuery({
    queryKey: ["application", app.id, "explanation", app.status],
    queryFn: () => getExplanation(app.id),
    enabled: decided || app.status === "NEEDS_MORE_INFO",
  });
  const offers = useQuery({
    queryKey: ["application", app.id, "offers", app.status],
    queryFn: () => getOffers(app.id),
    enabled: app.status === "APPROVED" || app.status === "COUNTER_OFFER",
  });
  const score = useQuery({
    queryKey: ["application", app.id, "score", app.riskScore],
    queryFn: () => getScore(app.id),
    enabled: decided && scored,
    retry: false,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["application", app.id] });
  const accept = useMutation({
    mutationFn: (offerId: string) => acceptOffer(app.id, offerId),
    onSuccess: refresh,
    onError: (error) => toast.error(error.message),
  });
  const sign = useMutation({
    mutationFn: () => signOffer(app.id),
    onSuccess: () => {
      toast.success("Документы подписаны");
      refresh();
    },
    onError: (error) => toast.error(error.message),
  });

  if (processingStatuses.includes(app.status)) {
    return (
      <div className="flex items-center gap-3 rounded-2xl border border-border bg-card p-5">
        <Loader2 className="size-5 shrink-0 animate-spin text-primary" />
        <div>
          <p className="font-semibold">{statusLabels[app.status]}</p>
          <p className="text-sm text-muted-foreground">
            {app.status === "MANUAL_REVIEW"
              ? "Заявку смотрит специалист. Обычно это занимает до 15 минут."
              : "Страница обновится сама, как только будет решение"}
          </p>
        </div>
      </div>
    );
  }

  if (app.status === "SIGNING") {
    return (
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-primary bg-card p-5">
        <div>
          <p className="font-semibold">
            Осталось подписать договор на {formatRub(app.approvedAmount ?? app.requestedAmount)}
          </p>
          <p className="text-sm text-muted-foreground">Подпись по коду из SMS. Для демо код 1234 подставится сам.</p>
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
      <div className="flex items-start gap-4 rounded-2xl border border-success bg-success/10 p-5">
        <PartyPopper className="size-6 shrink-0 text-success" />
        <div>
          <p className="font-heading text-xl font-bold">
            {formatRub(app.approvedAmount ?? app.requestedAmount)} уже{" "}
            <span className="font-accent font-medium italic">на счёте</span>
          </p>
          <p className="text-sm text-muted-foreground">
            {app.approvedTermMonths ? `${formatMonths(app.approvedTermMonths)}. ` : ""}График платежей придёт в
            приложение банка.
          </p>
        </div>
      </div>
    );
  }

  if (!decided && app.status !== "NEEDS_MORE_INFO") return null;

  return (
    <section className="space-y-4">
      {explanation.isPending ? (
        <Skeleton className="h-28 w-full rounded-2xl" />
      ) : explanation.isError ? (
        <ErrorState error={explanation.error} onRetry={() => explanation.refetch()} />
      ) : (
        <div className="space-y-4 rounded-2xl border border-border bg-card p-5">
          <p className="text-lg font-semibold">{explanation.data.summary}</p>
          <div className="grid gap-4 md:grid-cols-2">
            {explanation.data.reasons.length > 0 && (
              <div>
                <p className="font-hand text-xl text-muted-foreground">почему так</p>
                <ul className="mt-1 list-disc space-y-1 pl-5 text-sm">
                  {explanation.data.reasons.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
              </div>
            )}
            {explanation.data.nextSteps.length > 0 && (
              <div>
                <p className="font-hand text-xl text-muted-foreground">что можно сделать</p>
                <ul className="mt-1 list-disc space-y-1 pl-5 text-sm">
                  {explanation.data.nextSteps.map((s) => (
                    <li key={s}>{s}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}

      {offers.data && offers.data.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-xl font-semibold">
            {offers.data.length > 1 ? "Выберите предложение" : "Ваше предложение"}
          </h2>
          <div className="grid gap-4 md:grid-cols-3">
            {offers.data.map((offer, i) => (
              <div
                key={offer.id}
                className={cn(
                  "flex flex-col gap-3 rounded-2xl border bg-card p-5",
                  i === 0 ? "border-primary" : "border-border",
                )}
              >
                <p className="text-sm text-muted-foreground">{offerTypeLabels[offer.type] ?? offer.type}</p>
                <p className="font-heading text-2xl font-bold">{formatRub(offer.amount)}</p>
                <dl className="space-y-1 text-sm">
                  <OfferRow label="Срок" value={formatMonths(offer.termMonths)} />
                  <OfferRow label="Ставка" value={formatRate(offer.interestRate)} />
                  <OfferRow label="Платёж" value={formatRub(offer.monthlyPayment)} />
                  <OfferRow label="Переплата" value={formatRub(offer.overpayment)} />
                </dl>
                <Button
                  className="mt-auto h-11 rounded-xl"
                  variant={i === 0 ? "default" : "outline"}
                  disabled={accept.isPending}
                  onClick={() => accept.mutate(offer.id)}
                >
                  {accept.isPending && accept.variables === offer.id && <Loader2 className="animate-spin" />}
                  Принять
                </Button>
              </div>
            ))}
          </div>
        </div>
      )}

      {app.status === "REJECTED" && (
        <Link href="/quiz" className={cn(buttonVariants({ variant: "outline" }), "h-11 rounded-xl px-6")}>
          Подобрать другой продукт
        </Link>
      )}

      {score.data && <ScoreFactors score={score.data} />}
    </section>
  );
}

function OfferRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}
