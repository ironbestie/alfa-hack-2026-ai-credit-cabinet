import { mockManager } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type { ManagerDecisionRequest, MessageResponse, PendingApplication } from "./types";

export function getPendingApplications() {
  if (USE_MOCKS) return mockManager.pending();
  return request<PendingApplication[]>("/api/manager/applications/pending", { role: "manager" });
}

export function makeDecision(id: string, body: ManagerDecisionRequest) {
  if (USE_MOCKS) return mockManager.decide(id, body);
  return request<MessageResponse>(`/api/manager/applications/${id}/decision`, {
    method: "POST",
    body,
    role: "manager",
  });
}
