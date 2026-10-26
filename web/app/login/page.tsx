"use client";

import { Briefcase, Building2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { setRole, type Role } from "@/lib/session";

const roles: { role: Role; title: string; text: string; href: string; icon: typeof Building2 }[] = [
  {
    role: "client",
    title: "Я предприниматель",
    text: "Узнать лимит, оформить заявку, следить за решением и подписать договор.",
    href: "/applications",
    icon: Building2,
  },
  {
    role: "manager",
    title: "Я сотрудник банка",
    text: "Очередь заявок на ручной проверке, карточка заявки и решение по ней.",
    href: "/manager",
    icon: Briefcase,
  },
];

export default function LoginPage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  function enter(role: Role, href: string) {
    setRole(role);
    queryClient.clear();
    router.push(href);
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8 px-4 py-16 sm:px-6">
      <div className="space-y-2 text-center">
        <h1 className="text-4xl font-bold">
          Вход в <span className="font-accent font-medium text-primary italic">кабинет</span>
        </h1>
        <p className="text-muted-foreground">Демо-вход без пароля: выберите, за кого хотите посмотреть кабинет</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {roles.map(({ role, title, text, href, icon: Icon }) => (
          <button
            key={role}
            type="button"
            onClick={() => enter(role, href)}
            className="group flex flex-col gap-4 rounded-3xl border border-border bg-card p-6 text-left transition-colors hover:border-primary"
          >
            <span className="flex size-12 items-center justify-center rounded-2xl bg-primary/15 text-primary">
              <Icon className="size-6" />
            </span>
            <span className="font-heading text-xl font-bold">{title}</span>
            <span className="text-sm text-muted-foreground">{text}</span>
            <span className="font-hand text-xl text-primary opacity-0 transition-opacity group-hover:opacity-100">
              войти →
            </span>
          </button>
        ))}
      </div>
      <p className="text-center font-plain text-xs text-muted-foreground">
        В демо используются тестовые учётные записи client и manager. В рабочей версии здесь будет вход через Альфа-ID.
      </p>
    </div>
  );
}
