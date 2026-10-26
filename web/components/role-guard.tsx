"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { setRole, useRole, type Role } from "@/lib/session";

const titles: Record<Role, string> = {
  client: "Этот раздел для клиентов",
  manager: "Этот раздел для сотрудников банка",
};

export function RoleGuard({ role, children }: { role: Role; children: React.ReactNode }) {
  const current = useRole();
  const queryClient = useQueryClient();

  if (current === role) return children;

  return (
    <div className="mx-auto max-w-xl space-y-4 px-4 py-20 text-center sm:px-6">
      <h1 className="text-2xl font-bold">{titles[role]}</h1>
      <p className="text-muted-foreground">Войдите с подходящей ролью, чтобы продолжить.</p>
      <Button
        className="h-11 rounded-xl px-6"
        onClick={() => {
          setRole(role);
          queryClient.clear();
        }}
      >
        Войти как {role === "manager" ? "сотрудник банка" : "клиент"}
      </Button>
    </div>
  );
}
