import type { Metadata } from "next";
import { Suspense } from "react";
import { ApplyWizard } from "@/components/apply/apply-wizard";
import { Skeleton } from "@/components/ui/skeleton";

export const metadata: Metadata = {
  title: "Оформление заявки · Кредитный кабинет",
};

export default function ApplyPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <h1 className="mb-8 text-3xl font-bold md:text-4xl">
        Оформление <span className="font-accent font-medium text-primary italic">заявки</span>
      </h1>
      <Suspense fallback={<Skeleton className="h-96 w-full rounded-2xl" />}>
        <ApplyWizard />
      </Suspense>
    </div>
  );
}
