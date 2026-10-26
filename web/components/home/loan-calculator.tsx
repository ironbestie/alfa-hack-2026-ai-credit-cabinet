"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useDeferredValue, useState } from "react";
import { recommendProducts } from "@/api/quiz";
import type { LoanPurpose } from "@/api/types";
import { ErrorState } from "@/components/error-state";
import { ProductCard } from "@/components/product-card";
import { PurposePicker } from "@/components/purpose-picker";
import { RangeField } from "@/components/range-field";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatRubShort } from "@/lib/format";
import { cn } from "@/lib/utils";

export function LoanCalculator() {
  const [purpose, setPurpose] = useState<LoanPurpose>("ЗАКУПКА");
  const [amount, setAmount] = useState(3_000_000);
  const [termMonths, setTermMonths] = useState(24);

  const query = { purpose, amount: useDeferredValue(amount), termMonths: useDeferredValue(termMonths) };
  const recommendations = useQuery({
    queryKey: ["quiz", query],
    queryFn: () => recommendProducts(query),
    placeholderData: keepPreviousData,
  });

  const best = recommendations.data?.[0];

  return (
    <div className="flex flex-col gap-10 rounded-3xl border border-border bg-card p-6 md:p-10 lg:flex-row">
      <div className="space-y-8 lg:w-3/5">
        <div>
          <h2 className="text-3xl font-bold">
            Подобрать <span className="font-accent font-medium text-primary italic">кредит</span>
          </h2>
          <p className="mt-2 text-muted-foreground">Выберите цель, и мы покажем подходящий продукт</p>
        </div>
        <div className="space-y-3">
          <span className="text-sm text-muted-foreground">Цель кредита</span>
          <PurposePicker value={purpose} onChange={setPurpose} />
        </div>
        <RangeField
          label="Сумма"
          value={amount}
          min={100_000}
          max={30_000_000}
          step={100_000}
          onChange={setAmount}
          format={formatRubShort}
        />
        <RangeField
          label="Срок"
          value={termMonths}
          min={1}
          max={60}
          step={1}
          onChange={setTermMonths}
          format={(v) => `${v} мес.`}
        />
      </div>
      <div className="lg:w-2/5">
        {recommendations.isPending ? (
          <Skeleton className="h-80 w-full rounded-2xl" />
        ) : recommendations.isError ? (
          <ErrorState error={recommendations.error} onRetry={() => recommendations.refetch()} />
        ) : best ? (
          <ProductCard product={best} amount={amount} termMonths={termMonths} recommended>
            <Link href="/apply" className={cn(buttonVariants(), "h-11 rounded-xl font-semibold")}>
              Оформить
            </Link>
          </ProductCard>
        ) : (
          <div className="flex h-full flex-col justify-center gap-2 rounded-2xl border border-dashed border-border p-6 text-center">
            <p className="font-semibold">Под такие условия продукта нет</p>
            <p className="text-sm text-muted-foreground">Попробуйте изменить сумму или срок</p>
          </div>
        )}
      </div>
    </div>
  );
}
