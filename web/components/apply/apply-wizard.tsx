"use client";

import { useMutation } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { createApplication, signConsents, submitApplication } from "@/api/applications";
import type { CompanyLookup, CreditProduct } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { saveClientInn } from "@/lib/session";
import { zodResolver } from "@/lib/zod-resolver";
import { CompanyStep } from "./company-step";
import { ConfirmStep } from "./confirm-step";
import { ProductStep } from "./product-step";
import { applySchema, stepFields, stepTitles, type ApplyValues } from "./schema";
import { TermsStep } from "./terms-step";

export function ApplyWizard() {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [company, setCompany] = useState<CompanyLookup | null>(null);
  const [product, setProduct] = useState<CreditProduct | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  const form = useForm<ApplyValues>({
    resolver: zodResolver(applySchema),
    defaultValues: {
      inn: "",
      productId: "",
      amount: 3_000_000,
      termMonths: 24,
      protection: false,
      consentBki: false,
      consentPersonalData: false,
    },
  });

  const send = useMutation({
    mutationFn: async (values: ApplyValues) => {
      idempotencyKey.current ??= crypto.randomUUID();
      const application = await createApplication(
        {
          companyId: company!.id!,
          productId: values.productId,
          requestedAmount: values.amount,
          requestedTermMonths: values.termMonths,
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
    onError: (error) => form.setError("root", { message: error.message }),
  });

  async function next() {
    if (!(await form.trigger(stepFields[step]))) return;
    setStep((s) => s + 1);
  }

  const canSend = !!company?.id;
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
        {step === 1 && <ProductStep onProductChange={setProduct} />}
        {step === 2 && product && <TermsStep product={product} />}
        {step === 3 && product && <ConfirmStep product={product} />}

        {isLast && !canSend && (
          <p className="text-sm text-muted-foreground">
            Отправим заявку, когда проверим компанию в ФНС. Черновик сохранится.
          </p>
        )}

        {step > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-6">
            <Button variant="ghost" onClick={() => setStep((s) => s - 1)} disabled={send.isPending}>
              Назад
            </Button>
            {isLast ? (
              <Button
                className="h-11 rounded-xl px-6 font-semibold"
                disabled={!canSend || send.isPending}
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
