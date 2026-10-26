"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useRole, type Role } from "@/lib/session";
import { cn } from "@/lib/utils";

const links: Record<Role | "guest", { href: string; label: string }[]> = {
  guest: [
    { href: "/", label: "Главная" },
    { href: "/quiz", label: "Подобрать кредит" },
  ],
  client: [
    { href: "/", label: "Главная" },
    { href: "/quiz", label: "Подобрать кредит" },
    { href: "/applications", label: "Мои заявки" },
  ],
  manager: [
    { href: "/manager", label: "Очередь заявок" },
    { href: "/manager/applications", label: "Все заявки" },
    { href: "/manager/dlq", label: "Сбои обработки" },
  ],
};

function Links({ pathname, role }: { pathname: string | null; role: Role | null }) {
  const items = links[role ?? "guest"];
  const activeHref = pathname
    ? items
        .map((l) => l.href)
        .filter((href) => (href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`)))
        .sort((a, b) => b.length - a.length)[0]
    : null;

  return items.map((link) => (
    <Link
      key={link.href}
      href={link.href}
      className={cn(
        "transition-colors hover:text-foreground",
        link.href === activeHref ? "text-foreground" : "text-muted-foreground",
      )}
    >
      {link.label}
    </Link>
  ));
}

export function NavLinks() {
  return <Links pathname={usePathname()} role={useRole()} />;
}

export function StaticNavLinks() {
  return <Links pathname={null} role={null} />;
}
