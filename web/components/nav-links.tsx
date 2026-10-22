"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const links = [
  { href: "/", label: "Главная" },
  { href: "/quiz", label: "Подобрать кредит" },
  { href: "/applications", label: "Заявки" },
  { href: "/manager", label: "Кабинет менеджера" },
];

function Links({ pathname }: { pathname: string | null }) {
  return links.map((link) => {
    const active = pathname !== null && (link.href === "/" ? pathname === "/" : pathname.startsWith(link.href));
    return (
      <Link
        key={link.href}
        href={link.href}
        className={cn(
          "transition-colors hover:text-foreground",
          active ? "text-foreground" : "text-muted-foreground",
        )}
      >
        {link.label}
      </Link>
    );
  });
}

export function NavLinks() {
  return <Links pathname={usePathname()} />;
}

export function StaticNavLinks() {
  return <Links pathname={null} />;
}
