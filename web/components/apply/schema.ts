import { z } from "zod";
import { isInnFormat, isValidInn } from "@/lib/inn";

export const applySchema = z.object({
  inn: z
    .string()
    .trim()
    .refine(isInnFormat, "ИНН состоит из 10 или 12 цифр")
    .refine(isValidInn, "Проверьте ИНН: в нём ошибка"),
  purpose: z.enum(["ЗАКУПКА", "ОБОРОТ", "РАЗРЫВ", "ОБОРУДОВАНИЕ", "ГОСКОНТРАКТ", "РЕМОНТ", "РЕФИНАНСИРОВАНИЕ"], {
    error: "Выберите цель кредита",
  }),
  productId: z.string().min(1, "Выберите продукт"),
  amount: z.number().min(100_000, "Минимальная сумма 100 000 ₽"),
  termMonths: z.number().int().min(1, "Минимальный срок 1 месяц"),
  protection: z.boolean(),
  consentBki: z.boolean().refine(Boolean, "Без этого согласия мы не сможем оценить заявку"),
  consentPersonalData: z.boolean().refine(Boolean, "Без этого согласия мы не сможем оформить заявку"),
});

export type ApplyValues = z.infer<typeof applySchema>;

export const stepFields: (keyof ApplyValues)[][] = [
  ["inn"],
  ["purpose", "productId"],
  ["amount", "termMonths"],
  ["consentBki", "consentPersonalData", "protection"],
];

export const stepTitles = ["Компания", "Цель и продукт", "Сумма и срок", "Согласия и цена"];

export const PROTECTION = { rateDiscount: 3.5, cost: 60_000 };
