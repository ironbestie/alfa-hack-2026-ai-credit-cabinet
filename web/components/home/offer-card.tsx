"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { ApiError } from "@/api/http";
import { getPreapprovedOffer } from "@/api/offers";
import type { ConfidenceLevel } from "@/api/types";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMonths, formatRate, formatRub } from "@/lib/format";
import { useClientInn } from "@/lib/session";
import { cn } from "@/lib/utils";

const confidence: Record<ConfidenceLevel, { label: string; hint: string }> = {
  CALCULATED: { label: "Расчёт", hint: "Примерная сумма по открытым данным" },
  PREAPPROVED: { label: "Предодобрено", hint: "Финальное решение после проверки заявки" },
  APPROVED: { label: "Одобрено", hint: "Можно подписывать договор" },
};

const levels: ConfidenceLevel[] = ["CALCULATED", "PREAPPROVED", "APPROVED"];

function Frame({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative w-full max-w-sm">
      <div className="absolute inset-0 rounded-full bg-gradient-to-br from-primary/30 to-transparent blur-3xl" />
      <div className="relative flex min-h-72 flex-col justify-between gap-6 rounded-3xl border border-border bg-card p-6">
        {children}
      </div>
    </div>
  );
}

export function OfferCard() {
  const inn = useClientInn();
  const offer = useQuery({
    queryKey: ["offer", "preapproved", inn],
    queryFn: () => getPreapprovedOffer(inn!),
    enabled: !!inn,
  });

  const unknownCompany = offer.error instanceof ApiError && offer.error.status === 404;

  if (!inn || unknownCompany) {
    return (
      <Frame>
        <div className="space-y-2">
          <p className="text-xs text-muted-foreground">Предварительный лимит</p>
          <p className="font-heading text-2xl font-bold">
            Узнайте свой лимит <span className="font-accent font-medium text-primary italic">за минуту</span>
          </p>
          <p className="text-sm text-muted-foreground">
            Введите ИНН, и мы покажем, сколько готовы дать и на каких условиях, ещё до заявки.
          </p>
        </div>
        <Link href="/apply" className={cn(buttonVariants(), "h-11 rounded-xl font-semibold")}>
          Ввести ИНН
        </Link>
      </Frame>
    );
  }

  if (offer.isPending) {
    return (
      <Frame>
        <div className="space-y-3">
          <Skeleton className="h-3 w-32" />
          <Skeleton className="h-10 w-48" />
          <Skeleton className="h-4 w-40" />
        </div>
        <Skeleton className="h-11 w-full" />
      </Frame>
    );
  }

  if (offer.isError) {
    return (
      <Frame>
        <div className="space-y-2">
          <p className="text-xs text-muted-foreground">Предварительный лимит</p>
          <p className="font-heading text-xl font-bold">Не получилось рассчитать лимит</p>
          <p className="text-sm text-muted-foreground">{offer.error.message}</p>
        </div>
        <button
          type="button"
          onClick={() => offer.refetch()}
          className={cn(buttonVariants({ variant: "outline" }), "h-11 rounded-xl")}
        >
          Повторить
        </button>
      </Frame>
    );
  }

  const data = offer.data;
  const current = levels.indexOf(data.confidenceLevel);

  return (
    <Frame>
      <div className="space-y-4">
        <div className="flex items-center justify-between text-xs">
          <span className="text-muted-foreground">Предварительный лимит</span>
          <span className="font-semibold text-primary">● {confidence[data.confidenceLevel].label}</span>
        </div>
        <div>
          <p className="font-heading text-4xl font-bold">до {formatRub(data.maxAmount)}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Ставка от {formatRate(data.interestRate)} · до {formatMonths(data.termMonths)}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            Платёж ≈ {formatRub(data.monthlyPayment)} в месяц, переплата ≈ {formatRub(data.overpayment)}
          </p>
        </div>
        <ol className="grid grid-cols-3 gap-1" aria-label="Уровень уверенности">
          {levels.map((level, i) => (
            <li key={level} className="space-y-1">
              <div className={cn("h-1 rounded-full", i <= current ? "bg-primary" : "bg-muted")} />
              <span className={cn("text-[11px]", i === current ? "text-foreground" : "text-muted-foreground")}>
                {confidence[level].label}
              </span>
            </li>
          ))}
        </ol>
        <p className="font-hand text-xl leading-tight text-muted-foreground">
          {confidence[data.confidenceLevel].hint}
        </p>
      </div>
      <Link href="/apply" className={cn(buttonVariants(), "h-11 rounded-xl font-semibold")}>
        Получить деньги
      </Link>
    </Frame>
  );
}
