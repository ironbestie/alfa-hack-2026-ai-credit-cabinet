"use client";

import type { LoanPurpose } from "@/api/types";
import { cn } from "@/lib/utils";

export const purposes: { value: LoanPurpose; label: string }[] = [
  { value: "ЗАКУПКА", label: "Закупка товара" },
  { value: "РАЗРЫВ", label: "Кассовый разрыв" },
  { value: "ОБОРУДОВАНИЕ", label: "Оборудование" },
  { value: "ГОСКОНТРАКТ", label: "Госконтракт" },
  { value: "РЕМОНТ", label: "Ремонт, новая точка" },
  { value: "РЕФИНАНСИРОВАНИЕ", label: "Рефинансирование" },
];

type PurposePickerProps = {
  value: LoanPurpose | undefined;
  onChange: (value: LoanPurpose) => void;
};

export function PurposePicker({ value, onChange }: PurposePickerProps) {
  return (
    <div role="radiogroup" className="grid grid-cols-2 gap-2 sm:grid-cols-3">
      {purposes.map((purpose) => {
        const selected = purpose.value === value;
        return (
          <button
            key={purpose.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(purpose.value)}
            className={cn(
              "rounded-xl border px-3 py-3 text-left text-sm transition-colors",
              selected
                ? "border-primary bg-primary/10 text-foreground"
                : "border-border bg-card text-muted-foreground hover:bg-accent hover:text-foreground",
            )}
          >
            {purpose.label}
          </button>
        );
      })}
    </div>
  );
}
