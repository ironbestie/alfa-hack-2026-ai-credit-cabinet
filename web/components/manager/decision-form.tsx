"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { makeDecision } from "@/api/manager";
import type { Application, ManagerDecisionType } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { statusLabels } from "@/lib/statuses";
import { cn } from "@/lib/utils";
import { zodResolver } from "@/lib/zod-resolver";

const actions: { type: ManagerDecisionType; label: string; reasons: { code: string; label: string }[] }[] = [
  {
    type: "APPROVE",
    label: "Одобрить",
    reasons: [{ code: "MANUAL_OK", label: "Данные подтверждены" }],
  },
  {
    type: "COUNTER_OFFER",
    label: "Предложить другие условия",
    reasons: [
      { code: "MANUAL_LIMIT", label: "Сумма выше посильной" },
      { code: "MANUAL_TERM", label: "Нужен другой срок" },
    ],
  },
  {
    type: "REQUEST_DOCS",
    label: "Запросить документы",
    reasons: [
      { code: "DOCS_REQUIRED", label: "Нужна выписка по счёту" },
      { code: "DOCS_REVENUE", label: "Нужно подтвердить выручку" },
    ],
  },
  {
    type: "REJECT",
    label: "Отказать",
    reasons: [
      { code: "MANUAL_REJECT", label: "Высокий риск" },
      { code: "STOP_FACTOR", label: "Стоп-фактор в данных" },
    ],
  },
];

const schema = z
  .object({
    decisionType: z.enum(["APPROVE", "COUNTER_OFFER", "REQUEST_DOCS", "REJECT"]),
    approvedAmount: z.number({ error: "Укажите сумму" }).min(100_000, "Минимум 100 000 ₽"),
    approvedTermMonths: z.number({ error: "Укажите срок" }).int().min(1, "Минимум 1 месяц").max(84, "Максимум 84 месяца"),
    reasonCode: z.string().min(1),
    comment: z.string().trim(),
  })
  .refine((v) => v.decisionType === "APPROVE" || v.decisionType === "COUNTER_OFFER" || v.comment.length >= 5, {
    path: ["comment"],
    message: "Напишите клиенту, что нужно сделать",
  });

type DecisionValues = z.infer<typeof schema>;

export function DecisionForm({ application }: { application: Application }) {
  const queryClient = useQueryClient();
  const form = useForm<DecisionValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      decisionType: "APPROVE",
      approvedAmount: application.requestedAmount,
      approvedTermMonths: application.requestedTermMonths,
      reasonCode: "MANUAL_OK",
      comment: "",
    },
  });
  const decisionType = useWatch({ control: form.control, name: "decisionType" });
  const action = actions.find((a) => a.type === decisionType)!;
  const withTerms = decisionType === "APPROVE" || decisionType === "COUNTER_OFFER";

  const decide = useMutation({
    mutationFn: (values: DecisionValues) =>
      makeDecision(application.id, {
        decisionType: values.decisionType,
        approvedAmount: withTerms ? values.approvedAmount : null,
        approvedTermMonths: withTerms ? values.approvedTermMonths : null,
        reasonCode: values.reasonCode,
        comment: values.comment || action.reasons.find((r) => r.code === values.reasonCode)?.label || action.label,
      }),
    onSuccess: () => {
      toast.success("Решение отправлено клиенту");
      queryClient.invalidateQueries({ queryKey: ["application", application.id] });
      queryClient.invalidateQueries({ queryKey: ["manager"] });
    },
    onError: (error) => toast.error(error.message),
  });

  if (application.status !== "MANUAL_REVIEW") {
    return (
      <div className="space-y-2 rounded-2xl border border-border bg-card p-5">
        <h2 className="text-lg font-semibold">Решение</h2>
        <p className="text-sm text-muted-foreground">
          Сейчас заявка в статусе «{statusLabels[application.status]}». Решение можно принять, только когда она на ручной
          проверке.
        </p>
      </div>
    );
  }

  const errors = form.formState.errors;

  return (
    <form
      className="space-y-5 rounded-2xl border border-primary/50 bg-card p-5"
      onSubmit={form.handleSubmit((values) => decide.mutate(values))}
    >
      <h2 className="text-lg font-semibold">
        Ваше <span className="font-accent font-medium text-primary italic">решение</span>
      </h2>

      <Controller
        control={form.control}
        name="decisionType"
        render={({ field }) => (
          <div role="radiogroup" className="grid grid-cols-2 gap-2">
            {actions.map((a) => (
              <button
                key={a.type}
                type="button"
                role="radio"
                aria-checked={field.value === a.type}
                onClick={() => {
                  field.onChange(a.type);
                  form.setValue("reasonCode", a.reasons[0].code);
                  if (a.type === "APPROVE") {
                    form.setValue("approvedAmount", application.requestedAmount);
                    form.setValue("approvedTermMonths", application.requestedTermMonths);
                  }
                }}
                className={cn(
                  "rounded-xl border px-3 py-2.5 text-left text-sm transition-colors",
                  field.value === a.type
                    ? a.type === "REJECT"
                      ? "border-destructive bg-destructive/10"
                      : "border-primary bg-primary/10"
                    : "border-border text-muted-foreground hover:text-foreground",
                )}
              >
                {a.label}
              </button>
            ))}
          </div>
        )}
      />

      {withTerms && (
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="approvedAmount">Сумма, ₽</Label>
            <Input
              id="approvedAmount"
              type="number"
              step={100_000}
              disabled={decisionType === "APPROVE"}
              aria-invalid={!!errors.approvedAmount}
              {...form.register("approvedAmount", { valueAsNumber: true })}
            />
            {errors.approvedAmount && <p className="text-xs text-destructive">{errors.approvedAmount.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="approvedTermMonths">Срок, мес.</Label>
            <Input
              id="approvedTermMonths"
              type="number"
              disabled={decisionType === "APPROVE"}
              aria-invalid={!!errors.approvedTermMonths}
              {...form.register("approvedTermMonths", { valueAsNumber: true })}
            />
            {errors.approvedTermMonths && <p className="text-xs text-destructive">{errors.approvedTermMonths.message}</p>}
          </div>
        </div>
      )}

      <div className="space-y-1.5">
        <Label htmlFor="reasonCode">Причина</Label>
        <select
          id="reasonCode"
          className="h-9 w-full rounded-lg border border-input bg-input/30 px-3 text-sm"
          {...form.register("reasonCode")}
        >
          {action.reasons.map((r) => (
            <option key={r.code} value={r.code}>
              {r.label}
            </option>
          ))}
        </select>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="comment">
          Комментарий для клиента
          {!withTerms && <span className="text-destructive"> обязательно</span>}
        </Label>
        <Textarea
          id="comment"
          rows={3}
          placeholder={
            decisionType === "REQUEST_DOCS"
              ? "Например: загрузите выписку по счёту за 6 месяцев"
              : decisionType === "REJECT"
                ? "Коротко объясните клиенту причину отказа"
                : "Необязательно"
          }
          aria-invalid={!!errors.comment}
          {...form.register("comment")}
        />
        {errors.comment && <p className="text-xs text-destructive">{errors.comment.message}</p>}
      </div>

      <Button
        type="submit"
        variant={decisionType === "REJECT" ? "destructive" : "default"}
        className="h-11 w-full rounded-xl font-semibold"
        disabled={decide.isPending}
      >
        {decide.isPending && <Loader2 className="animate-spin" />}
        {action.label}
      </Button>
    </form>
  );
}
