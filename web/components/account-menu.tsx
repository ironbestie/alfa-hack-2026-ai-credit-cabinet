"use client";

import Link from "next/link";
import { useRole } from "@/lib/session";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function AccountMenu() {
  const role = useRole();

  if (!role) {
    return (
      <div className="flex items-center gap-2">
        <Link href="/login" className={cn(buttonVariants({ variant: "ghost" }), "h-10 rounded-xl px-4")}>
          Войти
        </Link>
        <Link href="/apply" className={cn(buttonVariants(), "h-10 rounded-xl px-5 font-semibold")}>
          Оформить заявку
        </Link>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <Link
        href="/login"
        title="Сменить роль"
        className="flex items-center gap-2 rounded-xl border border-border px-3 py-1.5 text-sm transition-colors hover:border-primary"
      >
        <span className="flex size-6 items-center justify-center rounded-full bg-primary/15 text-xs font-bold text-primary">
          {role === "manager" ? "М" : "К"}
        </span>
        <span className="hidden sm:inline">{role === "manager" ? "Сотрудник банка" : "Клиент"}</span>
      </Link>
      {role === "client" && (
        <Link href="/apply" className={cn(buttonVariants(), "hidden h-10 rounded-xl px-5 font-semibold sm:inline-flex")}>
          Оформить заявку
        </Link>
      )}
    </div>
  );
}
