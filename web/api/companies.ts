import { mockCompanies } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type { Company, CompanyLookup, Page, PageParams } from "./types";

export function lookupCompany(inn: string) {
  if (USE_MOCKS) return mockCompanies.lookup(inn);
  return request<CompanyLookup>("/api/companies/lookup", { query: { inn } });
}

export function listCompanies(params: PageParams = {}) {
  if (USE_MOCKS) return mockCompanies.list(params);
  return request<Page<Company>>("/api/companies", { query: params });
}

export async function findCompanyByInn(inn: string) {
  const page = await listCompanies({ page: 0, size: 100 });
  return page.items.find((company) => company.inn === inn) ?? null;
}
