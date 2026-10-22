import { mockApplications } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type {
  Application,
  ApplicationEvent,
  CreateApplicationRequest,
  Explanation,
  MessageResponse,
  Offer,
} from "./types";

const base = "/api/applications";

export function createApplication(body: CreateApplicationRequest, idempotencyKey: string) {
  if (USE_MOCKS) return mockApplications.create(body);
  return request<Application>(base, { method: "POST", body, idempotencyKey });
}

export function listApplications() {
  if (USE_MOCKS) return mockApplications.list();
  return request<Application[]>(base);
}

export function getApplication(id: string) {
  if (USE_MOCKS) return mockApplications.get(id);
  return request<Application>(`${base}/${id}`);
}

export function signConsents(id: string) {
  if (USE_MOCKS) return mockApplications.signConsents(id);
  return request<MessageResponse>(`${base}/${id}/consents`, { method: "POST" });
}

export function submitApplication(id: string) {
  if (USE_MOCKS) return mockApplications.submit(id);
  return request<MessageResponse>(`${base}/${id}/submit`, { method: "POST" });
}

export function acceptOffer(id: string) {
  if (USE_MOCKS) return mockApplications.acceptOffer(id);
  return request<MessageResponse>(`${base}/${id}/accept-offer`, { method: "POST" });
}

export function signOffer(id: string) {
  if (USE_MOCKS) return mockApplications.sign(id);
  return request<MessageResponse>(`${base}/${id}/sign`, { method: "POST" });
}

export function uploadDocument(id: string, file: File) {
  if (USE_MOCKS) return mockApplications.uploadDocument(id, file);
  const body = new FormData();
  body.append("file", file);
  return request<MessageResponse>(`${base}/${id}/documents`, { method: "POST", body });
}

export function getTimeline(id: string) {
  if (USE_MOCKS) return mockApplications.timeline(id);
  return request<ApplicationEvent[]>(`${base}/${id}/timeline`);
}

export function getOffers(id: string) {
  if (USE_MOCKS) return mockApplications.offers(id);
  return request<Offer[]>(`${base}/${id}/offers`);
}

export function getExplanation(id: string) {
  if (USE_MOCKS) return mockApplications.explanation(id);
  return request<Explanation>(`${base}/${id}/explanation`);
}

export function statusStreamUrl(id: string) {
  return USE_MOCKS ? null : `${base}/${id}/stream`;
}
