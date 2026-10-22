import { mockCompanies } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type { CompanyLookup } from "./types";

export function lookupCompany(inn: string) {
  if (USE_MOCKS) return mockCompanies.lookup(inn);
  return request<CompanyLookup>(`/api/companies/lookup?inn=${encodeURIComponent(inn)}`);
}
