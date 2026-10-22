"use client";

import { Controller, useFormContext, useWatch } from "react-hook-form";
import type { CreditProduct } from "@/api/types";
import { Checkbox } from "@/components/ui/checkbox";
import { Switch } from "@/components/ui/switch";
import { calculateLoan } from "@/lib/credit";
import { formatRate, formatRub } from "@/lib/format";
import { cn } from "@/lib/utils";
import { PROTECTION, type ApplyValues } from "./schema";

const consents = [
  {
    name: "consentBki",
    title: "Согласен на запрос в бюро кредитных историй",
    text: "Действует 1 год. Нужно для оценки риска.",
  },
  {
    name: "consentPersonalData",
    title: "Согласен на обработку персональных данных",
    text: "Для формирования заявки и проверки данных.",
  },
] as const;

export function ConfirmStep({ product }: { product: CreditProduct }) {
  const { control, formState } = useFormContext<ApplyValues>();
  const [amount, termMonths, protection] = useWatch({ control, name: ["amount", "termMonths", "protection"] });

  const without = calculateLoan(amount, product.baseRate, termMonths);
  const withRate = product.baseRate - PROTECTION.rateDiscount;
  const withOption = calculateLoan(amount, withRate, termMonths);
  const withTotal = withOption.total + PROTECTION.cost;

  const columns = [
    { key: false, title: "Без опции", rate: product.baseRate, payment: without.payment, total: without.total },
    { key: true, title: "С опцией", rate: withRate, payment: withOption.payment, total: withTotal },
  ];
  const cheaper = withTotal < without.total;

  return (
    <div className="space-y-8">
      <div className="space-y-4">
        <h2 className="text-2xl font-bold">Согласия</h2>
        {consents.map((consent) => (
          <Controller
            key={consent.name}
            control={control}
            name={consent.name}
            render={({ field, fieldState }) => (
              <div className="space-y-1">
                <label className="flex cursor-pointer gap-3 rounded-xl border border-border bg-card p-4">
                  <Checkbox
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    aria-invalid={!!fieldState.error}
                    className="mt-0.5"
                  />
                  <span>
                    <span className="block font-medium">{consent.title}</span>
                    <span className="text-sm text-muted-foreground">{consent.text}</span>
                  </span>
                </label>
                {fieldState.error && <p className="text-sm text-destructive">{fieldState.error.message}</p>}
              </div>
            )}
          />
        ))}
      </div>

      <div className="space-y-4">
        <h2 className="text-2xl font-bold">Сколько это стоит</h2>
        <Controller
          control={control}
          name="protection"
          render={({ field }) => (
            <label className="flex cursor-pointer items-start justify-between gap-4 rounded-xl border border-border bg-card p-4">
              <span>
                <span className="block font-medium">«Защита бизнеса»</span>
                <span className="text-sm text-muted-foreground">
                  Снижает ставку на {formatRate(PROTECTION.rateDiscount)}. Стоимость опции {formatRub(PROTECTION.cost)}.
                  Можно не подключать, на решение это не влияет.
                </span>
              </span>
              <Switch checked={field.value} onCheckedChange={field.onChange} />
            </label>
          )}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          {columns.map((col) => (
            <dl
              key={col.title}
              className={cn(
                "space-y-3 rounded-2xl border bg-card p-5 text-sm",
                col.key === protection ? "border-primary" : "border-border",
              )}
            >
              <div className="flex items-center justify-between">
                <span className="font-semibold">{col.title}</span>
                {col.key === cheaper && <span className="font-hand text-2xl leading-none text-primary">выгоднее!</span>}
              </div>
              <Row label="Ставка" value={formatRate(col.rate)} />
              <Row label="Платёж" value={formatRub(col.payment)} />
              <Row label="Вернёте всего" value={formatRub(col.total)} />
            </dl>
          ))}
        </div>
        <p className="font-plain text-xs text-muted-foreground">
          Расчёт предварительный. Точные условия и ПСК покажем в договоре до подписания.
        </p>
      </div>
      {formState.errors.root && <p className="text-sm text-destructive">{formState.errors.root.message}</p>}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-semibold">{value}</dd>
    </div>
  );
}
