"use client";

import { useMutation } from "@tanstack/react-query";
import { CheckCircle2, Loader2, TriangleAlert } from "lucide-react";
import { useState } from "react";
import { useFormContext } from "react-hook-form";
import { lookupCompany } from "@/api/companies";
import { ApiError } from "@/api/http";
import type { CompanyLookup } from "@/api/types";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { formatDate, pluralize, yearsSince } from "@/lib/format";
import type { ApplyValues } from "./schema";

type CompanyStepProps = {
  company: CompanyLookup | null;
  onCompanyChange: (company: CompanyLookup | null) => void;
  onNext: () => void;
};

export function CompanyStep({ company, onCompanyChange, onNext }: CompanyStepProps) {
  const { register, trigger, getValues, formState } = useFormContext<ApplyValues>();
  const [manual, setManual] = useState(false);

  const lookup = useMutation({
    mutationFn: lookupCompany,
    onSuccess: (data) => {
      setManual(false);
      onCompanyChange(data);
    },
    onError: () => onCompanyChange(null),
  });

  async function search() {
    onCompanyChange(null);
    if (!(await trigger("inn"))) return;
    lookup.mutate(getValues("inn"));
  }

  const innField = register("inn", { onChange: () => lookup.reset() });
  const serviceDown = lookup.error instanceof ApiError && lookup.error.status >= 500;
  const notFound = lookup.error instanceof ApiError && lookup.error.status === 404;
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

      {lookup.isPending && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          Ищем компанию в ЕГРЮЛ
        </p>
      )}

      {notFound && (
        <Alert>
          <TriangleAlert />
          <AlertTitle>Компания не найдена</AlertTitle>
          <AlertDescription>Проверьте ИНН. Если всё верно, введите данные вручную.</AlertDescription>
        </Alert>
      )}

      {serviceDown && !manual && (
        <Alert>
          <TriangleAlert />
          <AlertTitle>Сервис ФНС сейчас не отвечает</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-3">
            <span>Данные можно ввести вручную, заявка не потеряется.</span>
            <Button size="sm" variant="outline" onClick={() => setManual(true)}>
              Ввести вручную
            </Button>
            <Button size="sm" variant="ghost" onClick={search}>
              Проверить ещё раз
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {manual && <ManualCompanyForm inn={getValues("inn")} onRetry={search} />}

      {company && liquidated && (
        <Alert variant="destructive">
          <TriangleAlert />
          <AlertTitle>{company.name} ликвидирована</AlertTitle>
          <AlertDescription>Кредит можно оформить только на действующую компанию.</AlertDescription>
        </Alert>
      )}

      {company && !liquidated && (
        <>
          <CompanyCard company={company} />
          <div className="flex flex-wrap gap-3">
            <Button className="h-11 rounded-xl px-6 font-semibold" onClick={onNext}>
              Всё верно, дальше
            </Button>
            <Button variant="outline" className="h-11 rounded-xl px-6" onClick={() => setManual(true)}>
              Исправить вручную
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

function CompanyCard({ company }: { company: CompanyLookup }) {
  const years = yearsSince(company.registrationDate);
  const rows = [
    ["ОГРН", company.ogrn],
    ["Руководитель", company.directorName],
    ["ОКВЭД", [company.okved, company.okvedDescription].filter(Boolean).join(" · ")],
    [
      "Зарегистрирована",
      `${formatDate(company.registrationDate)} (${years} ${pluralize(years, "год", "года", "лет")})`,
    ],
    ["Категория", company.category],
  ].filter(([, value]) => value);

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

function ManualCompanyForm({ inn, onRetry }: { inn: string; onRetry: () => void }) {
  return (
    <div className="space-y-4 rounded-2xl border border-border bg-card p-5">
      <div>
        <h3 className="font-semibold">Данные компании</h3>
        <p className="text-sm text-muted-foreground">
          Сохраним черновик и отправим заявку, как только ФНС ответит.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="manual-inn">ИНН</Label>
          <Input id="manual-inn" value={inn} readOnly />
        </div>
        <div className="space-y-2">
          <Label htmlFor="manual-name">Название</Label>
          <Input id="manual-name" placeholder="ООО «Ромашка»" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="manual-ogrn">ОГРН</Label>
          <Input id="manual-ogrn" inputMode="numeric" maxLength={15} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="manual-director">Руководитель</Label>
          <Input id="manual-director" />
        </div>
      </div>
      <Button variant="outline" onClick={onRetry}>
        Проверить в ФНС ещё раз
      </Button>
    </div>
  );
}
