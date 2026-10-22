import { mockQuiz } from "@/mocks/handlers";
import { request, USE_MOCKS } from "./http";
import type { CreditProduct, QuizRequest } from "./types";

export function recommendProducts(body: QuizRequest) {
  if (USE_MOCKS) return mockQuiz.recommend(body);
  return request<CreditProduct[]>("/api/quiz/recommend", { method: "POST", body, role: "public" });
}
