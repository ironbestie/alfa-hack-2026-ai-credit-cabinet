"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { getExplanation, getScore } from "@/api/applications";
import { listCompanies } from "@/api/companies";
import { DocumentsPanel } from "@/components/documents-panel";
import { ErrorState } from "@/components/error-state";
import { DecisionForm } from "@/components/manager/decision-form";
import { ScoreFactors } from "@/components/score-factors";
import { StatusBadge } from "@/components/status-badge";
import { Timeline } from "@/components/timeline";
import { Skeleton } from "@/components/ui/skeleton";
import { useApplicationStatus } from "@/hooks/use-application-status";
import { formatDate, formatMonths, formatRate, formatRub, formatRubShort } from "@/lib/format";

export function ManagerApplicationCard() {
  const { id } = useParams<{ id: string }>();
  const { application, timeline } = useApplicationStatus(id);
  const app = application.data;

  const company = useQuery({
    queryKey: ["companies", "all"],
    queryFn: () => listCompanies({ page: 0, size: 100 }),
    select: (page) => page.items.find((c) => c.id === app?.company.id) ?? null,
    enabled: !!app,
    staleTime: 5 * 60_000,
  });
  const score = useQuery({
    queryKey: ["application", id, "score", app?.riskScore],
    queryFn: () => getScore(id),
    enabled: app?.riskScore != null,
    retry: false,
  });
  const explanation = useQuery({
    queryKey: ["application", id, "explanation", app?.status],
    queryFn: () => getExplanation(id),
    enabled: !!app && app.status !== "DRAFT",
  });

  if (application.isPending) {
    return <Skeleton className="h-96 w-full rounded-2xl" />;
  }
  if (application.isError || !app) {
    return <ErrorState title="Не удалось загрузить заявку" error={application.error} onRetry={() => application.refetch()} />;
  }

  const c = company.data;
  const debtLoad = c && c.annualRevenue > 0 ? Math.round((c.totalDebt / c.annualRevenue) * 100) : null;

  return (
    <div className="space-y-6">
      <Link href="/manager" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        К очереди
      </Link>

      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold">{app.company.name}</h1>
        <StatusBadge status={app.status} />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          <section className="grid gap-4 rounded-2xl border border-border bg-card p-5 text-sm sm:grid-cols-3">
            <Field label="Запрошено" value={formatRub(app.requestedAmount)} strong />
            <Field label="Срок" value={formatMonths(app.requestedTermMonths)} />
            <Field label="Продукт" value={`${app.product.name}, от ${formatRate(app.product.baseRate)}`} />
            <Field label="Цель" value={app.purpose ?? "—"} />
            <Field label="Залог" value={app.hasCollateral ? "Есть" : "Нет"} />
            <Field label="Подана" value={formatDate(app.createdAt)} />
            {app.approvedAmount !== null && (
              <Field label="Одобрено" value={`${formatRub(app.approvedAmount)} на ${formatMonths(app.approvedTermMonths ?? app.requestedTermMonths)}`} />
            )}
            {app.rejectionReason && (
              <Field
                label={app.status === "REJECTED" ? "Причина отказа" : "Почему на ручной проверке"}
                value={app.rejectionReason}
              />
            )}
          </section>

          <section className="space-y-3 rounded-2xl border border-border bg-card p-5">
            <h2 className="text-lg font-semibold">Компания</h2>
            {company.isPending ? (
              <Skeleton className="h-20 w-full" />
            ) : c ? (
              <dl className="grid gap-4 text-sm sm:grid-cols-3">
                <Field label="ИНН / ОГРН" value={`${c.inn} / ${c.ogrn}`} />
                <Field label="Руководитель" value={c.directorName} />
                <Field label="Категория" value={c.category} />
                <Field label="ОКВЭД" value={`${c.okved} · ${c.okvedDescription}`} />
                <Field label="Зарегистрирована" value={formatDate(c.registrationDate)} />
                <Field label="Клиент банка" value={formatMonths(c.bankRelationshipMonths)} />
                <Field label="Выручка за год" value={formatRubShort(c.annualRevenue)} />
                <Field label="Долги" value={`${formatRubShort(c.totalDebt)}${debtLoad !== null ? ` · ${debtLoad}% выручки` : ""}`} />
                <Field label="Налоговый долг" value={c.taxDebt > 0 ? formatRub(c.taxDebt) : "нет"} />
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">
                ИНН {app.company.inn}, ОГРН {app.company.ogrn}
              </p>
            )}
          </section>

          {score.data && <ScoreFactors score={score.data} />}

          {explanation.data && (
            <section className="space-y-2 rounded-2xl border border-border bg-card p-5 text-sm">
              <h2 className="text-lg font-semibold">Резюме для специалиста</h2>
              <p>{explanation.data.summary}</p>
              {explanation.data.reasons.length > 0 && (
                <ul className="list-disc space-y-1 pl-5 text-muted-foreground">
                  {explanation.data.reasons.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
              )}
              <p className="font-plain text-xs text-muted-foreground">
                {explanation.data.generatedByAi
                  ? "Текст подготовлен ИИ-ассистентом, решение принимает специалист."
                  : "Текст собран по шаблону из факторов скоринга, решение принимает специалист."}
              </p>
            </section>
          )}

          <DocumentsPanel applicationId={app.id} canUpload={false} />

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">История</h2>
            {timeline.data ? <Timeline events={timeline.data.items} /> : <Skeleton className="h-24 w-full" />}
          </section>
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <DecisionForm application={app} />
        </aside>
      </div>
    </div>
  );
}

function Field({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={strong ? "font-heading text-lg font-semibold" : "font-medium"}>{value}</dd>
    </div>
  );
}
