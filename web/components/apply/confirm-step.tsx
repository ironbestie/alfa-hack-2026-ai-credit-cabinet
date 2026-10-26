"use client";

import { Controller, useFormContext, useWatch } from "react-hook-form";
import type { CreditProduct } from "@/api/types";
import { Checkbox } from "@/components/ui/checkbox";
import { calculateLoan } from "@/lib/credit";
import { formatMonths, formatRate, formatRub } from "@/lib/format";
import { purposeLabels, type ApplyValues } from "./schema";

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
  const [amount, termMonths, purpose, hasCollateral] = useWatch({
    control,
    name: ["amount", "termMonths", "purpose", "hasCollateral"],
  });
  const { payment, total, overpayment } = calculateLoan(amount, product.baseRate, termMonths);

  return (
    <div className="space-y-8">
      <div className="space-y-4">
        <h2 className="text-2xl font-bold">
          Проверьте <span className="font-accent font-medium text-primary italic">условия</span>
        </h2>
        <dl className="grid gap-4 rounded-2xl border border-border bg-card p-5 text-sm sm:grid-cols-2">
          <Row label="Продукт" value={product.name} />
          <Row label="Цель" value={purposeLabels[purpose]} />
          <Row label="Сумма" value={formatRub(amount)} />
          <Row label="Срок" value={formatMonths(termMonths)} />
          <Row label="Ставка" value={`от ${formatRate(product.baseRate)}`} />
          <Row label="Залог" value={hasCollateral ? "Есть" : "Без залога"} />
          <Row label="Платёж в месяц" value={`≈ ${formatRub(payment)}`} strong />
          <Row label="Вернёте всего" value={`≈ ${formatRub(total)}`} strong />
        </dl>
        <p className="font-plain text-xs text-muted-foreground">
          Переплата ≈ {formatRub(overpayment)}. Расчёт предварительный: точные условия придут вместе с решением, до
          подписания договора.
        </p>
      </div>

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
      {formState.errors.root && <p className="text-sm text-destructive">{formState.errors.root.message}</p>}
    </div>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={strong ? "font-heading text-lg font-semibold" : "font-medium"}>{value}</dd>
    </div>
  );
}
