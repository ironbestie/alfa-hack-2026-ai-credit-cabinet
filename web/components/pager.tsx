import type { Page } from "@/api/types";
import { Button } from "@/components/ui/button";

export function Pager({ page, onChange }: { page: Page<unknown>; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-between gap-3 text-sm">
      <Button variant="outline" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        Назад
      </Button>
      <span className="text-muted-foreground">
        Страница {page.page + 1} из {page.totalPages}
      </span>
      <Button variant="outline" disabled={page.page + 1 >= page.totalPages} onClick={() => onChange(page.page + 1)}>
        Дальше
      </Button>
    </div>
  );
}
