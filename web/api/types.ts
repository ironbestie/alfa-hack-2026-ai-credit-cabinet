// Повторяют DTO из src/main/kotlin/com/alfabank/credit. После описания эндпоинтов
// в OpenAPI заменить на сгенерированные типы: npm run gen:api

export type ApplicationStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "ENRICHMENT"
  | "SCORING"
  | "MANUAL_REVIEW"
  | "NEEDS_MORE_INFO"
  | "APPROVED"
  | "COUNTER_OFFER"
  | "REJECTED"
  | "SIGNING"
  | "FUNDED"
  | "CANCELLED"
  | "EXPIRED";

export type CompanyLookup = {
  id?: string;
  inn: string;
  name: string;
  ogrn: string;
  directorName: string;
  registrationDate: string;
  okved: string;
  okvedDescription?: string;
  category?: string;
  status?: "active" | "liquidated";
};

export type CreditProduct = {
  id: string;
  name: string;
  description: string;
  minAmount: number;
  maxAmount: number;
  minTerm: number;
  maxTerm: number;
  baseRate: number;
  tags: string;
};

export type ConfidenceLevel = "CALCULATED" | "PREAPPROVED" | "APPROVED";

export type PreapprovedOffer = {
  inn: string;
  maxAmount: number;
  termMonths: number;
  interestRate: number;
  confidenceLevel: ConfidenceLevel;
};

export type LoanPurpose =
  | "ЗАКУПКА"
  | "ОБОРОТ"
  | "РАЗРЫВ"
  | "ОБОРУДОВАНИЕ"
  | "ГОСКОНТРАКТ"
  | "РЕМОНТ"
  | "РЕФИНАНСИРОВАНИЕ";

export type QuizRequest = {
  purpose: LoanPurpose;
  amount: number;
  termMonths: number;
};

export type CreateApplicationRequest = {
  companyId: string;
  productId: string;
  requestedAmount: number;
  requestedTermMonths: number;
};

export type Application = {
  id: string;
  status: ApplicationStatus;
  requestedAmount: number;
  requestedTermMonths: number;
  riskScore: number | null;
  scoringDetails: string | null;
  approvedAmount: number | null;
  rejectionReason: string | null;
};

export type ApplicationEvent = {
  timestamp: string;
  eventType: string;
  description: string;
  actor: string;
};

export type Offer = {
  id: string;
  amount: number;
  termMonths: number;
  interestRate: number;
  type: string;
};

export type Explanation = {
  summary: string;
  reasons: string[];
  nextSteps: string[];
};

export type StatusUpdate = {
  status: ApplicationStatus;
  message: string;
};

export type PendingApplication = {
  id: string;
  amount: number;
  score: number | null;
  reason: string | null;
};

export type ManagerDecisionType = "APPROVE" | "COUNTER_OFFER" | "REQUEST_DOCUMENTS" | "REJECT";

export type ManagerDecisionRequest = {
  decisionType: ManagerDecisionType;
  approvedAmount: number | null;
  approvedTermMonths: number | null;
  comment: string;
};

export type MessageResponse = {
  message: string;
};
