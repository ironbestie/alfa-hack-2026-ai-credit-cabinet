import { mockProducts } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type { CreditProduct, Page, PageParams } from "./types";

export function listProducts(params: PageParams = {}) {
  if (USE_MOCKS) return mockProducts.list(params);
  return request<Page<CreditProduct>>("/api/products", { query: params, role: "public" });
}
