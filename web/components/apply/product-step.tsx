"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect } from "react";
import { useFormContext, useWatch } from "react-hook-form";
import { recommendProducts } from "@/api/quiz";
import type { CreditProduct } from "@/api/types";
import { ErrorState } from "@/components/error-state";
import { ProductCard } from "@/components/product-card";
import { PurposePicker } from "@/components/purpose-picker";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { ApplyValues } from "./schema";

type ProductStepProps = {
  onProductChange: (product: CreditProduct) => void;
  preferredProductId?: string | null;
};

export function ProductStep({ onProductChange, preferredProductId }: ProductStepProps) {
  const { control, setValue, formState } = useFormContext<ApplyValues>();
  const [purpose, productId, amount, termMonths] = useWatch({
    control,
    name: ["purpose", "productId", "amount", "termMonths"],
  });

  const products = useQuery({
    queryKey: ["quiz", { purpose, amount, termMonths }],
    queryFn: () => recommendProducts({ purpose, amount, termMonths }),
    enabled: !!purpose,
  });

  const suggested = products.data?.find((p) => p.id === preferredProductId) ?? products.data?.[0];
  useEffect(() => {
    if (!suggested || productId) return;
    selectProduct(suggested);
  });

  function selectProduct(product: CreditProduct) {
    setValue("productId", product.id, { shouldValidate: true });
    setValue("amount", clamp(amount, product.minAmount, product.maxAmount));
    setValue("termMonths", clamp(termMonths, product.minTerm, product.maxTerm));
    onProductChange(product);
  }

  return (
    <div className="space-y-8">
      <div className="space-y-4">
        <div>
          <h2 className="text-2xl font-bold">На что нужны деньги?</h2>
          <p className="mt-1 text-muted-foreground">Выберите цель, подберём подходящий продукт</p>
        </div>
        <PurposePicker
          value={purpose}
          onChange={(value) => {
            setValue("purpose", value, { shouldValidate: true });
            setValue("productId", "");
          }}
        />
        {formState.errors.purpose && <p className="text-sm text-destructive">{formState.errors.purpose.message}</p>}
        <Link href="/quiz" className="inline-block text-sm text-muted-foreground underline-offset-4 hover:underline">
          Не знаю, что выбрать, пройти квиз →
        </Link>
      </div>

      {purpose && (
        <div className="space-y-4">
          <h3 className="text-lg font-semibold">Подходит</h3>
          {products.isPending ? (
            <div className="grid gap-4 md:grid-cols-2">
              <Skeleton className="h-72 rounded-2xl" />
              <Skeleton className="h-72 rounded-2xl" />
            </div>
          ) : products.isError ? (
            <ErrorState error={products.error} onRetry={() => products.refetch()} />
          ) : products.data.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-border p-6 text-center text-muted-foreground">
              Под эту цель сейчас нет продукта. Попробуйте другую цель.
            </p>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {products.data.map((product, i) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  amount={amount}
                  termMonths={termMonths}
                  recommended={i === 0}
                  selected={product.id === productId}
                >
                  <Button
                    variant={product.id === productId ? "default" : "outline"}
                    className="h-11 rounded-xl font-semibold"
                    onClick={() => selectProduct(product)}
                  >
                    {product.id === productId ? "Выбрано" : "Выбрать"}
                  </Button>
                </ProductCard>
              ))}
            </div>
          )}
          {formState.errors.productId && (
            <p className="text-sm text-destructive">{formState.errors.productId.message}</p>
          )}
        </div>
      )}
    </div>
  );
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}
