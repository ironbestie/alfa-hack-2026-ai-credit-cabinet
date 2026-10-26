"use client";

import { useMutation } from "@tanstack/react-query";
import { CheckCircle2, Loader2, TriangleAlert } from "lucide-react";
import { useFormContext } from "react-hook-form";
import { findCompanyByInn, lookupCompany } from "@/api/companies";
import { ApiError } from "@/api/http";
import type { Company } from "@/api/types";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TEST_INNS } from "@/mocks/data";
import { formatDate, formatRubShort, pluralize, yearsSince } from "@/lib/format";
import type { ApplyValues } from "./schema";

type CompanyStepProps = {
  company: Company | null;
  onCompanyChange: (company: Company | null) => void;
  onNext: () => void;
};

type LookupResult = { company: Company | null; okved: string };

const demoInns = [
  { inn: TEST_INNS.approved, label: "одобрение" },
  { inn: TEST_INNS.counterOffer, label: "контрпредложение" },
  { inn: TEST_INNS.rejected, label: "отказ" },
  { inn: TEST_INNS.manualReview, label: "к специалисту" },
];

export function CompanyStep({ company, onCompanyChange, onNext }: CompanyStepProps) {
  const { register, trigger, getValues, setValue, formState } = useFormContext<ApplyValues>();

  const lookup = useMutation({
    mutationFn: async (inn: string): Promise<LookupResult> => {
      const egrul = await lookupCompany(inn);
      const client = await findCompanyByInn(inn);
      return { company: client, okved: egrul.okved };
    },
    onSuccess: (data) => onCompanyChange(data.company),
    onError: () => onCompanyChange(null),
  });

  async function search() {
    onCompanyChange(null);
    if (!(await trigger("inn"))) return;
    lookup.mutate(getValues("inn"));
  }

  function pickDemo(inn: string) {
    setValue("inn", inn, { shouldValidate: true });
    search();
  }

  const innField = register("inn", { onChange: () => lookup.reset() });
  const error = lookup.error instanceof ApiError ? lookup.error : null;
  const notFound = error?.status === 404;
  const serviceDown = !!lookup.error && !notFound;
  const notClient = lookup.isSuccess && !lookup.data.company;
  const liquidated = company?.status === "liquidated";

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Введите ИНН компании</h2>
        <p className="mt-1 text-muted-foreground">
          Остальное подтянем сами из ЕГРЮЛ и ФНС. Это займёт пару секунд.
        </p>
      </div>

      <form
        className="flex flex-col gap-3 sm:flex-row sm:items-end"
        onSubmit={(e) => {
          e.preventDefault();
          search();
        }}
      >
        <div className="flex-1 space-y-2">
          <Label htmlFor="inn">ИНН</Label>
          <Input
            id="inn"
            inputMode="numeric"
            autoComplete="off"
            maxLength={12}
            placeholder="10 или 12 цифр"
            aria-invalid={!!formState.errors.inn}
            className="h-12 text-lg"
            {...innField}
          />
        </div>
        <Button type="submit" className="h-12 rounded-xl px-6" disabled={lookup.isPending}>
          {lookup.isPending && <Loader2 className="animate-spin" />}
          Найти
        </Button>
      </form>
      {formState.errors.inn && <p className="text-sm text-destructive">{formState.errors.inn.message}</p>}

      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-hand text-xl text-muted-foreground">для демо:</span>
        {demoInns.map((demo) => (
          <button
            key={demo.inn}
            type="button"
            onClick={() => pickDemo(demo.inn)}
            className="rounded-full border border-border px-3 py-1 text-muted-foreground transition-colors hover:border-primary hover:text-foreground"
          >
            {demo.inn} · {demo.label}
          </button>
        ))}
      </div>

      {lookup.isPending && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          Ищем компанию в ЕГРЮЛ
        </p>
      )}

      {notFound && (
        <Alert>
          <TriangleAlert />
          <AlertTitle>Компания не найдена в ЕГРЮЛ</AlertTitle>
          <AlertDescription>Проверьте ИНН: возможно, в нём опечатка.</AlertDescription>
        </Alert>
      )}

      {serviceDown && (
        <Alert>
          <TriangleAlert />
          <AlertTitle>Сервис проверки сейчас не отвечает</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-3">
            <span>{lookup.error?.message}. Данные не потеряются, попробуйте через минуту.</span>
            <Button size="sm" variant="outline" onClick={search}>
              Проверить ещё раз
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {notClient && (
        <Alert>
          <TriangleAlert />
          <AlertTitle>Компания пока не обслуживается в банке</AlertTitle>
          <AlertDescription>
            Онлайн-кредит доступен действующим клиентам. Откройте расчётный счёт, и через пару дней лимит появится здесь.
          </AlertDescription>
        </Alert>
      )}

      {company && liquidated && (
        <Alert variant="destructive">
          <TriangleAlert />
          <AlertTitle>{company.name} ликвидирована</AlertTitle>
          <AlertDescription>Кредит можно оформить только на действующую компанию.</AlertDescription>
        </Alert>
      )}

      {company && !liquidated && lookup.data && (
        <>
          <CompanyCard company={company} okved={lookup.data.okved} />
          <Button className="h-11 rounded-xl px-6 font-semibold" onClick={onNext}>
            Всё верно, дальше
          </Button>
        </>
      )}
    </div>
  );
}

function CompanyCard({ company, okved }: { company: Company; okved: string }) {
  const years = yearsSince(company.registrationDate);
  const rows = [
    ["ОГРН", company.ogrn],
    ["Руководитель", company.directorName],
    ["ОКВЭД", okved],
    [
      "Зарегистрирована",
      `${formatDate(company.registrationDate)} (${years} ${pluralize(years, "год", "года", "лет")})`,
    ],
    ["Категория", company.category],
    ["Выручка за год", formatRubShort(company.annualRevenue)],
  ];

  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <CheckCircle2 className="size-5 text-success" />
        <h3 className="text-lg font-semibold">{company.name}</h3>
        <Badge variant="secondary">Действующая</Badge>
      </div>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="text-muted-foreground">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
