import Link from "next/link";
import { Suspense } from "react";
import { AccountMenu } from "@/components/account-menu";
import { NavLinks, StaticNavLinks } from "@/components/nav-links";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/80 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex items-center gap-8">
          <Link href="/" className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-md bg-primary font-heading text-xl font-bold">
              А
            </span>
            <span className="hidden font-heading font-semibold sm:inline">Кредитный кабинет</span>
          </Link>
          <nav className="hidden items-center gap-6 text-sm md:flex">
            <Suspense fallback={<StaticNavLinks />}>
              <NavLinks />
            </Suspense>
          </nav>
        </div>
        <AccountMenu />
      </div>
    </header>
  );
}
