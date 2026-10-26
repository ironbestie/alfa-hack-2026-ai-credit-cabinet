"use client";

import { useMutation } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { createApplication, signConsents, submitApplication } from "@/api/applications";
import type { Company, CreditProduct, LoanPurpose } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { getRole, saveClientInn, setRole } from "@/lib/session";
import { zodResolver } from "@/lib/zod-resolver";
import { CompanyStep } from "./company-step";
import { ConfirmStep } from "./confirm-step";
import { ProductStep } from "./product-step";
import { applySchema, purposeLabels, stepFields, stepTitles, type ApplyValues } from "./schema";
import { TermsStep } from "./terms-step";

function numberParam(value: string | null, fallback: number) {
  const n = Number(value);
  return value && Number.isFinite(n) && n > 0 ? n : fallback;
}

export function ApplyWizard() {
  const router = useRouter();
  const params = useSearchParams();
  const [step, setStep] = useState(0);
  const [company, setCompany] = useState<Company | null>(null);
  const [product, setProduct] = useState<CreditProduct | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  const form = useForm<ApplyValues>({
    resolver: zodResolver(applySchema),
    defaultValues: {
      inn: "",
      purpose: (params.get("purpose") as LoanPurpose | null) ?? undefined,
      productId: "",
      amount: numberParam(params.get("amount"), 3_000_000),
      termMonths: numberParam(params.get("term"), 24),
      hasCollateral: false,
      consentBki: false,
      consentPersonalData: false,
    },
  });

  const send = useMutation({
    mutationFn: async (values: ApplyValues) => {
      if (getRole() !== "client") setRole("client");
      idempotencyKey.current ??= crypto.randomUUID();
      const application = await createApplication(
        {
          companyId: company!.id,
          productId: values.productId,
          requestedAmount: values.amount,
          requestedTermMonths: values.termMonths,
          purpose: purposeLabels[values.purpose],
          hasCollateral: values.hasCollateral,
        },
        idempotencyKey.current,
      );
      await signConsents(application.id);
      await submitApplication(application.id);
      return application;
    },
    onSuccess: (application, values) => {
      saveClientInn(values.inn);
      router.push(`/applications/${application.id}`);
    },
    onError: (error) => {
      idempotencyKey.current = null;
      form.setError("root", { message: error.message });
    },
  });

  async function next() {
    if (!(await form.trigger(stepFields[step]))) return;
    setStep((s) => s + 1);
  }

  const isLast = step === stepTitles.length - 1;

  return (
    <FormProvider {...form}>
      <div className="space-y-8">
        <div className="space-y-3">
          <div className="flex items-center justify-between text-sm">
            <span className="font-semibold">{stepTitles[step]}</span>
            <span className="text-muted-foreground">
              Шаг {step + 1} из {stepTitles.length}
            </span>
          </div>
          <Progress value={((step + 1) / stepTitles.length) * 100} />
        </div>

        {step === 0 && <CompanyStep company={company} onCompanyChange={setCompany} onNext={next} />}
        {step === 1 && <ProductStep onProductChange={setProduct} preferredProductId={params.get("product")} />}
        {step === 2 && product && company && <TermsStep product={product} inn={company.inn} />}
        {step === 3 && product && <ConfirmStep product={product} />}

        {step > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-6">
            <Button variant="ghost" onClick={() => setStep((s) => s - 1)} disabled={send.isPending}>
              Назад
            </Button>
            {isLast ? (
              <Button
                className="h-11 rounded-xl px-6 font-semibold"
                disabled={send.isPending}
                onClick={form.handleSubmit((values) => send.mutate(values))}
              >
                {send.isPending && <Loader2 className="animate-spin" />}
                Отправить заявку
              </Button>
            ) : (
              <Button className="h-11 rounded-xl px-6 font-semibold" onClick={next}>
                Дальше
              </Button>
            )}
          </div>
        )}
      </div>
    </FormProvider>
  );
}
