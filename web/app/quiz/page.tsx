"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { recommendProducts } from "@/api/quiz";
import type { LoanPurpose } from "@/api/types";
import { ErrorState } from "@/components/error-state";
import { ProductCard } from "@/components/product-card";
import { PurposePicker, purposes } from "@/components/purpose-picker";
import { RangeField } from "@/components/range-field";
import { Button, buttonVariants } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMonths, formatRub, formatRubShort } from "@/lib/format";
import { cn } from "@/lib/utils";

const questions = ["На что нужны деньги?", "Сколько нужно?", "На какой срок?"];

export default function QuizPage() {
  const [step, setStep] = useState(0);
  const [purpose, setPurpose] = useState<LoanPurpose | undefined>();
  const [amount, setAmount] = useState(3_000_000);
  const [termMonths, setTermMonths] = useState(12);
  const done = step === questions.length;

  const recommendations = useQuery({
    queryKey: ["quiz", { purpose, amount, termMonths }],
    queryFn: () => recommendProducts({ purpose: purpose!, amount, termMonths }),
    enabled: done && !!purpose,
  });

  const purposeLabel = purposes.find((p) => p.value === purpose)?.label;

  return (
    <div className="mx-auto max-w-3xl space-y-8 px-4 py-10 sm:px-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-bold md:text-4xl">
          Подберём кредит <span className="font-accent font-medium text-primary italic">за минуту</span>
        </h1>
        <p className="text-muted-foreground">Три вопроса, и мы покажем, что подойдёт вашему бизнесу</p>
      </div>

      {!done && (
        <div className="space-y-8 rounded-3xl border border-border bg-card p-6 md:p-8">
          <div className="space-y-3">
            <div className="flex justify-between text-sm text-muted-foreground">
              <span>
                Вопрос {step + 1} из {questions.length}
              </span>
            </div>
            <Progress value={((step + 1) / questions.length) * 100} />
          </div>
          <h2 className="text-2xl font-bold">{questions[step]}</h2>

          {step === 0 && (
            <PurposePicker
              value={purpose}
              onChange={(value) => {
                setPurpose(value);
                setStep(1);
              }}
            />
          )}
          {step === 1 && (
            <RangeField label="Сумма" value={amount} min={100_000} max={30_000_000} step={100_000} onChange={setAmount} format={formatRubShort} />
          )}
          {step === 2 && (
            <RangeField label="Срок" value={termMonths} min={1} max={60} step={1} onChange={setTermMonths} format={(v) => `${v} мес.`} />
          )}

          <div className="flex justify-between gap-3">
            <Button variant="ghost" disabled={step === 0} onClick={() => setStep((s) => s - 1)}>
              Назад
            </Button>
            {step > 0 && (
              <Button className="h-11 rounded-xl px-6" onClick={() => setStep((s) => s + 1)}>
                {step === questions.length - 1 ? "Показать варианты" : "Дальше"}
              </Button>
            )}
          </div>
        </div>
      )}

      {done && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card px-5 py-4 text-sm">
            <span>
              {purposeLabel} · {formatRub(amount)} · {formatMonths(termMonths)}
            </span>
            <Button variant="ghost" size="sm" onClick={() => setStep(0)}>
              Изменить ответы
            </Button>
          </div>

          {recommendations.isPending ? (
            <div className="grid gap-4 md:grid-cols-2">
              <Skeleton className="h-80 rounded-2xl" />
              <Skeleton className="h-80 rounded-2xl" />
            </div>
          ) : recommendations.isError ? (
            <ErrorState error={recommendations.error} onRetry={() => recommendations.refetch()} />
          ) : recommendations.data.length === 0 ? (
            <div className="space-y-3 rounded-2xl border border-dashed border-border p-8 text-center">
              <p className="font-semibold">Под такие условия продукта нет</p>
              <p className="text-sm text-muted-foreground">Попробуйте изменить сумму или срок</p>
              <Button variant="outline" onClick={() => setStep(1)}>
                Изменить сумму
              </Button>
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {recommendations.data.map((product, i) => {
                const params = new URLSearchParams({
                  purpose: purpose!,
                  amount: String(amount),
                  term: String(termMonths),
                  product: product.id,
                });
                return (
                  <ProductCard key={product.id} product={product} amount={amount} termMonths={termMonths} recommended={i === 0}>
                    <Link
                      href={`/apply?${params}`}
                      className={cn(buttonVariants({ variant: i === 0 ? "default" : "outline" }), "h-11 rounded-xl font-semibold")}
                    >
                      Оформить
                    </Link>
                  </ProductCard>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
