import type { CreditProduct } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { calculateLoan } from "@/lib/credit";
import { formatMonths, formatRate, formatRub, formatRubShort } from "@/lib/format";
import { cn } from "@/lib/utils";

export function parseTags(tags: string): string[] {
  try {
    const parsed: unknown = JSON.parse(tags);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

type ProductCardProps = {
  product: CreditProduct;
  amount: number;
  termMonths: number;
  recommended?: boolean;
  selected?: boolean;
  children?: React.ReactNode;
};

export function ProductCard({ product, amount, termMonths, recommended, selected, children }: ProductCardProps) {
  const { payment, overpayment } = calculateLoan(amount, product.baseRate, termMonths);

  return (
    <div
      className={cn(
        "flex flex-col gap-4 rounded-2xl border bg-card p-5 transition-colors",
        selected ? "border-primary" : "border-border",
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        {recommended && <span className="font-hand text-2xl leading-none text-primary">рекомендуем</span>}
        {parseTags(product.tags).map((tag) => (
          <Badge key={tag} variant="secondary">
            {tag}
          </Badge>
        ))}
      </div>
      <div>
        <h3 className="text-xl font-bold">{product.name}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{product.description}</p>
      </div>
      <dl className="grid grid-cols-3 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">Ставка</dt>
          <dd className="font-semibold">от {formatRate(product.baseRate)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Сумма</dt>
          <dd className="font-semibold">до {formatRubShort(product.maxAmount)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Срок</dt>
          <dd className="font-semibold">
            {product.minTerm}–{product.maxTerm} мес.
          </dd>
        </div>
      </dl>
      <dl className="grid grid-cols-2 gap-3 border-t border-border pt-4 text-sm">
        <div>
          <dt className="text-muted-foreground">Платёж в месяц</dt>
          <dd className="font-heading text-lg font-semibold">≈ {formatRub(payment)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Переплата за {formatMonths(termMonths)}</dt>
          <dd className="font-heading text-lg font-semibold">≈ {formatRub(overpayment)}</dd>
        </div>
      </dl>
      {children}
    </div>
  );
}
