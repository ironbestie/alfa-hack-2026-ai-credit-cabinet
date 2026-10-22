"use client";

import { useQuery } from "@tanstack/react-query";
import { TriangleAlert } from "lucide-react";
import { useFormContext, useWatch } from "react-hook-form";
import { getPreapprovedOffer } from "@/api/offers";
import type { CreditProduct } from "@/api/types";
import { RangeField } from "@/components/range-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { calculateLoan } from "@/lib/credit";
import { formatRate, formatRub, formatRubShort } from "@/lib/format";
import type { ApplyValues } from "./schema";

export function TermsStep({ product }: { product: CreditProduct }) {
  const { control, setValue } = useFormContext<ApplyValues>();
  const [inn, amount, termMonths] = useWatch({ control, name: ["inn", "amount", "termMonths"] });

  const offer = useQuery({
    queryKey: ["offer", "preapproved", inn],
    queryFn: () => getPreapprovedOffer(inn),
  });

  const { payment, overpayment } = calculateLoan(amount, product.baseRate, termMonths);
  const overLimit = offer.data && amount > offer.data.maxAmount;

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold">Сколько и на какой срок</h2>
        <p className="mt-1 text-muted-foreground">
          {product.name} · ставка от {formatRate(product.baseRate)}
        </p>
      </div>

      <RangeField
        label="Сумма"
        value={amount}
        min={product.minAmount}
        max={product.maxAmount}
        step={100_000}
        onChange={(v) => setValue("amount", v, { shouldValidate: true })}
        format={formatRubShort}
      />
      <RangeField
        label="Срок"
        value={termMonths}
        min={product.minTerm}
        max={product.maxTerm}
        step={1}
        onChange={(v) => setValue("termMonths", v, { shouldValidate: true })}
        format={(v) => `${v} мес.`}
      />

      <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-border bg-card p-5">
        <div>
          <dt className="text-sm text-muted-foreground">Платёж в месяц</dt>
          <dd className="font-heading text-2xl font-semibold">≈ {formatRub(payment)}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Переплата</dt>
          <dd className="font-heading text-2xl font-semibold">≈ {formatRub(overpayment)}</dd>
        </div>
      </dl>

      {overLimit && (
        <Alert>
          <TriangleAlert />
          <AlertTitle>Сумма выше предварительного лимита</AlertTitle>
          <AlertDescription>
            Сейчас мы готовы дать до {formatRub(offer.data.maxAmount)}. Заявку можно отправить, но решение может
            прийти со встречным предложением.
          </AlertDescription>
        </Alert>
      )}
    </div>
  );
}
