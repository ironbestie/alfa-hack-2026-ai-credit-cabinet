import Link from "next/link";
import { LoanCalculator } from "@/components/home/loan-calculator";
import { OfferCard } from "@/components/home/offer-card";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const steps = [
  { title: "Ввод ИНН", text: "Подтянем данные из ЕГРЮЛ и ФНС. Останется только подтвердить." },
  { title: "Выбор продукта", text: "Квиз из трёх вопросов подберёт кредит под вашу задачу." },
  { title: "Решение онлайн", text: "Оценка заявки от 2 минут, каждый этап видно в трекере." },
  { title: "Деньги на счёте", text: "Без визита в офис. Подписание по УКЭП или SMS." },
];

export default function HomePage() {
  return (
    <div className="mx-auto max-w-7xl space-y-20 px-4 pt-12 pb-20 sm:px-6 md:pt-16">
      <section className="flex flex-col items-start justify-between gap-10 lg:flex-row lg:items-center">
        <div className="max-w-2xl">
          <span className="mb-6 inline-flex items-center gap-2 rounded-full border border-border bg-card px-4 py-1.5 text-xs text-muted-foreground">
            <span className="size-1.5 rounded-full bg-primary" />
            Решение по заявке от 2 минут онлайн
          </span>
          <h1 className="mb-6 text-5xl leading-[1.05] font-bold md:text-7xl">
            Кредиты для бизнеса <span className="font-accent font-medium text-primary italic">на любые цели</span>
          </h1>
          <p className="mb-8 max-w-xl text-lg leading-relaxed text-muted-foreground">
            На оборудование, транспорт, модернизацию производства или пополнение оборотных средств. Без
            визита в офис.
          </p>
          <div className="flex flex-wrap gap-3">
            <Link href="/apply" className={cn(buttonVariants(), "h-12 rounded-xl px-7 font-semibold")}>
              Оформить заявку
            </Link>
            <Link
              href="/quiz"
              className={cn(buttonVariants({ variant: "outline" }), "h-12 rounded-xl px-7 font-semibold")}
            >
              Подобрать кредит
            </Link>
          </div>
        </div>
        <OfferCard />
      </section>

      <section>
        <LoanCalculator />
      </section>

      <section className="space-y-8">
        <h2 className="text-3xl font-bold">
          Как это <span className="font-accent font-medium text-primary italic">работает</span>
        </h2>
        <ol className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {steps.map((step, i) => (
            <li key={step.title} className="rounded-2xl border border-border bg-card p-6">
              <span className="font-hand text-4xl leading-none text-primary">{i + 1}</span>
              <h3 className="mt-3 text-lg font-semibold">{step.title}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{step.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="flex flex-col items-start justify-between gap-6 rounded-3xl border border-border bg-card p-6 md:flex-row md:items-center md:p-10">
        <div>
          <h2 className="text-2xl font-bold">
            Не знаете, <span className="font-accent font-medium text-primary italic">что выбрать?</span>
          </h2>
          <p className="mt-2 text-muted-foreground">
            Ответьте на три вопроса, и мы подберём продукт под вашу задачу за минуту.
          </p>
        </div>
        <Link href="/quiz" className={cn(buttonVariants(), "h-12 rounded-xl px-7 font-semibold")}>
          Пройти квиз
        </Link>
      </section>
    </div>
  );
}
