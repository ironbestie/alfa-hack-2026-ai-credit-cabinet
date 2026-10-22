import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function ComingSoon({ title, text }: { title: string; text: string }) {
  return (
    <div className="mx-auto max-w-2xl px-4 py-20 text-center sm:px-6">
      <h1 className="text-3xl font-bold">{title}</h1>
      <p className="mt-3 text-muted-foreground">{text}</p>
      <Link href="/" className={cn(buttonVariants({ variant: "outline" }), "mt-8 h-11 rounded-xl px-6")}>
        На главную
      </Link>
    </div>
  );
}
