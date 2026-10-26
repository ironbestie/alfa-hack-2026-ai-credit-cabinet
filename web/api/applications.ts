import { mockApplications } from "@/mocks/handlers";
import { request, requestBlob, USE_MOCKS } from "./http";
import type {
  Application,
  ApplicationDocument,
  ApplicationEvent,
  CreateApplicationRequest,
  Explanation,
  MessageResponse,
  Offer,
  Page,
  PageParams,
  Score,
} from "./types";

const base = "/api/applications";

export function createApplication(body: CreateApplicationRequest, idempotencyKey: string) {
  if (USE_MOCKS) return mockApplications.create(body);
  return request<Application>(base, { method: "POST", body, idempotencyKey });
}

export function listApplications(params: PageParams = {}) {
  if (USE_MOCKS) return mockApplications.list(params);
  return request<Page<Application>>(base, { query: params });
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

export function acceptOffer(id: string, offerId: string) {
  if (USE_MOCKS) return mockApplications.acceptOffer(id, offerId);
  return request<MessageResponse>(`${base}/${id}/accept-offer`, {
    method: "POST",
    body: { offerId },
    idempotencyKey: crypto.randomUUID(),
  });
}

export function signOffer(id: string) {
  if (USE_MOCKS) return mockApplications.sign(id);
  return request<MessageResponse>(`${base}/${id}/sign`, { method: "POST", idempotencyKey: crypto.randomUUID() });
}

export function uploadDocument(id: string, file: File) {
  if (USE_MOCKS) return mockApplications.uploadDocument(id, file);
  const body = new FormData();
  body.append("file", file);
  return request<MessageResponse>(`${base}/${id}/documents`, { method: "POST", body });
}

export function listDocuments(id: string) {
  if (USE_MOCKS) return mockApplications.documents(id);
  return request<ApplicationDocument[]>(`${base}/${id}/documents`);
}

export function downloadDocument(id: string, documentId: string) {
  if (USE_MOCKS) return mockApplications.downloadDocument(id, documentId);
  return requestBlob(`${base}/${id}/documents/${documentId}/download`);
}

export function getTimeline(id: string, params: PageParams = { page: 0, size: 100 }) {
  if (USE_MOCKS) return mockApplications.timeline(id, params);
  return request<Page<ApplicationEvent>>(`${base}/${id}/timeline`, { query: params });
}

export function getOffers(id: string) {
  if (USE_MOCKS) return mockApplications.offers(id);
  return request<Offer[]>(`${base}/${id}/offers`);
}

export function getScore(id: string) {
  if (USE_MOCKS) return mockApplications.score(id);
  return request<Score>(`${base}/${id}/score`);
}

export function getExplanation(id: string) {
  if (USE_MOCKS) return mockApplications.explanation(id);
  return request<Explanation>(`${base}/${id}/explanation`);
}

export function statusStreamUrl(id: string) {
  return USE_MOCKS ? null : `${base}/${id}/stream`;
}
