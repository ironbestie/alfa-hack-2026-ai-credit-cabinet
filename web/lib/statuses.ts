import type { ApplicationStatus } from "@/api/types";

export const statusLabels: Record<ApplicationStatus, string> = {
  DRAFT: "Черновик",
  SUBMITTED: "Заявка отправлена",
  ENRICHMENT: "Собираем данные",
  SCORING: "Оцениваем заявку",
  DECISION: "Принимаем решение",
  MANUAL_REVIEW: "Смотрит специалист",
  NEEDS_MORE_INFO: "Нужен документ",
  APPROVED: "Одобрено",
  COUNTER_OFFER: "Есть встречное предложение",
  REJECTED: "Не одобрено",
  SIGNING: "Подписание",
  FUNDED: "Деньги на счёте",
  CLOSED: "Закрыта",
  CANCELLED: "Отменена",
  EXPIRED: "Срок истёк",
};

export type StatusTone = "progress" | "action" | "success" | "danger" | "muted";

export const statusTone: Record<ApplicationStatus, StatusTone> = {
  DRAFT: "muted",
  SUBMITTED: "progress",
  ENRICHMENT: "progress",
  SCORING: "progress",
  DECISION: "progress",
  MANUAL_REVIEW: "progress",
  NEEDS_MORE_INFO: "action",
  APPROVED: "success",
  COUNTER_OFFER: "action",
  SIGNING: "action",
  REJECTED: "danger",
  FUNDED: "success",
  CLOSED: "muted",
  CANCELLED: "muted",
  EXPIRED: "muted",
};

export const processingStatuses: ApplicationStatus[] = ["SUBMITTED", "ENRICHMENT", "SCORING", "DECISION", "MANUAL_REVIEW"];

export const decidedStatuses: ApplicationStatus[] = ["APPROVED", "COUNTER_OFFER", "REJECTED"];

export const finalStatuses: ApplicationStatus[] = ["REJECTED", "FUNDED", "CLOSED", "CANCELLED", "EXPIRED"];

export const trackerSteps = [
  { key: "SUBMITTED", label: "Заявка отправлена", statuses: ["DRAFT", "SUBMITTED"] },
  { key: "CHECK", label: "Проверка данных", statuses: ["ENRICHMENT"] },
  { key: "SCORING", label: "Оценка заявки", statuses: ["SCORING", "DECISION", "MANUAL_REVIEW", "NEEDS_MORE_INFO"] },
  { key: "DECISION", label: "Решение", statuses: ["APPROVED", "COUNTER_OFFER", "REJECTED"] },
  { key: "SIGNING", label: "Подписание", statuses: ["SIGNING"] },
  { key: "FUNDED", label: "Деньги на счёте", statuses: ["FUNDED", "CLOSED"] },
] as const satisfies { key: string; label: string; statuses: ApplicationStatus[] }[];

export function trackerStepIndex(status: ApplicationStatus) {
  return trackerSteps.findIndex((step) => (step.statuses as readonly ApplicationStatus[]).includes(status));
}

export const offerTypeLabels: Record<string, string> = {
  REQUESTED: "Как вы просили",
  LIMIT: "Меньшая сумма",
  GUARANTEE: "С поручительством Корпорации МСП",
  COLLATERAL: "С залогом",
};

export const eventActorLabels: Record<string, string> = {
  client: "Клиент",
  manager: "Специалист",
  system: "Система",
};
