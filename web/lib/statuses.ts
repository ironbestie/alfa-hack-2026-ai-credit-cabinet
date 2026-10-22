import type { ApplicationStatus } from "@/api/types";

export const statusLabels: Record<ApplicationStatus, string> = {
  DRAFT: "Черновик",
  SUBMITTED: "Заявка отправлена",
  ENRICHMENT: "Проверяем данные",
  SCORING: "Оцениваем заявку",
  MANUAL_REVIEW: "Смотрит специалист",
  NEEDS_MORE_INFO: "Нужен документ",
  APPROVED: "Одобрено",
  COUNTER_OFFER: "Есть встречное предложение",
  REJECTED: "Не одобрено",
  SIGNING: "Подписание",
  FUNDED: "Деньги на счёте",
  CANCELLED: "Отменена",
  EXPIRED: "Срок истёк",
};

export const processingStatuses: ApplicationStatus[] = ["SUBMITTED", "ENRICHMENT", "SCORING", "MANUAL_REVIEW"];

export const finalStatuses: ApplicationStatus[] = ["REJECTED", "FUNDED", "CANCELLED", "EXPIRED"];

export const trackerSteps = [
  { key: "SUBMITTED", label: "Заявка отправлена", statuses: ["SUBMITTED"] },
  { key: "CHECK", label: "Проверка данных", statuses: ["ENRICHMENT", "NEEDS_MORE_INFO"] },
  { key: "SCORING", label: "Оценка заявки", statuses: ["SCORING", "MANUAL_REVIEW"] },
  { key: "DECISION", label: "Решение", statuses: ["APPROVED", "COUNTER_OFFER", "REJECTED"] },
  { key: "SIGNING", label: "Подписание", statuses: ["SIGNING"] },
  { key: "FUNDED", label: "Деньги на счёте", statuses: ["FUNDED"] },
] as const satisfies { key: string; label: string; statuses: ApplicationStatus[] }[];

export function trackerStepIndex(status: ApplicationStatus) {
  return trackerSteps.findIndex((step) => (step.statuses as readonly ApplicationStatus[]).includes(status));
}
