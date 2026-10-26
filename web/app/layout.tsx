import type { Metadata } from "next";
import { Caveat, Nunito, Playfair_Display, Roboto } from "next/font/google";
import { Providers } from "./providers";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

const roboto = Roboto({
  variable: "--font-sans",
  subsets: ["latin", "cyrillic"],
});

const nunito = Nunito({
  variable: "--font-display",
  subsets: ["latin", "cyrillic"],
});

const playfair = Playfair_Display({
  variable: "--font-curly",
  subsets: ["latin", "cyrillic"],
  style: ["italic"],
});

const caveat = Caveat({
  variable: "--font-handwritten",
  subsets: ["latin", "cyrillic"],
});

export const metadata: Metadata = {
  title: "Кредитный кабинет для бизнеса",
  description: "Кредиты для малого и среднего бизнеса онлайн",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="ru"
      className={`dark ${roboto.variable} ${nunito.variable} ${playfair.variable} ${caveat.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <Providers>
          <SiteHeader />
          <main className="flex-1">{children}</main>
          <footer className="border-t border-border">
            <div className="mx-auto max-w-7xl px-4 py-6 font-plain text-xs text-muted-foreground sm:px-6">
              © 2026 · Прототип для хакатона
            </div>
          </footer>
        </Providers>
      </body>
    </html>
  );
}
