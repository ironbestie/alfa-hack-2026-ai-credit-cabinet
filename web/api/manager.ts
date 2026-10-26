import { mockManager } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type { DeadLetter, ManagerDecisionRequest, MessageResponse, Page, PageParams, PendingApplication } from "./types";

export function getPendingApplications(params: PageParams = {}) {
  if (USE_MOCKS) return mockManager.pending(params);
  return request<Page<PendingApplication>>("/api/manager/applications/pending", { role: "manager", query: params });
}

export function makeDecision(id: string, body: ManagerDecisionRequest) {
  if (USE_MOCKS) return mockManager.decide(id, body);
  return request<MessageResponse>(`/api/manager/applications/${id}/decision`, {
    method: "POST",
    body,
    role: "manager",
  });
}

export function getDeadLetters(params: PageParams = {}) {
  if (USE_MOCKS) return mockManager.deadLetters(params);
  return request<Page<DeadLetter>>("/api/manager/dlq", { role: "manager", query: params });
}

export function retryDeadLetter(applicationId: string) {
  if (USE_MOCKS) return mockManager.retry();
  return request<MessageResponse>(`/api/manager/dlq/${applicationId}/retry`, { method: "POST", role: "manager" });
}
