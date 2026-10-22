import { mockOffers } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type { PreapprovedOffer } from "./types";

export function getPreapprovedOffer(inn: string) {
  if (USE_MOCKS) return mockOffers.preapproved(inn);
  return request<PreapprovedOffer>(`/api/offers/preapproved?inn=${encodeURIComponent(inn)}`, {
    role: "public",
  });
}
