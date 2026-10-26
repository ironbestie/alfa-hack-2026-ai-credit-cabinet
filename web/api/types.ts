// Повторяют DTO из src/main/kotlin/com/alfabank/credit/dto и BACKEND_FOR_FRONTEND.md

export type ApplicationStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "ENRICHMENT"
  | "SCORING"
  | "DECISION"
  | "MANUAL_REVIEW"
  | "NEEDS_MORE_INFO"
  | "APPROVED"
  | "COUNTER_OFFER"
  | "REJECTED"
  | "SIGNING"
  | "FUNDED"
  | "CLOSED"
  | "CANCELLED"
  | "EXPIRED";

export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type PageParams = {
  page?: number;
  size?: number;
};

export type CompanyLookup = {
  inn: string;
  name: string;
  ogrn: string;
  directorName: string;
  registrationDate: string;
  okved: string;
};

export type Company = {
  id: string;
  inn: string;
  ogrn: string;
  name: string;
  okved: string;
  okvedDescription: string;
  directorName: string;
  registrationDate: string;
  category: string;
  status: string;
  annualRevenue: number;
  totalDebt: number;
  taxDebt: number;
  bankRelationshipMonths: number;
};

export type CompanySummary = Pick<Company, "id" | "inn" | "ogrn" | "name" | "category" | "status">;

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

export type ProductSummary = Pick<CreditProduct, "id" | "name" | "baseRate">;

export type ConfidenceLevel = "CALCULATED" | "PREAPPROVED" | "APPROVED";

export type PreapprovedOffer = {
  inn: string;
  maxAmount: number;
  termMonths: number;
  interestRate: number;
  monthlyPayment: number;
  overpayment: number;
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
  purpose: string;
  hasCollateral: boolean;
};

export type Application = {
  id: string;
  status: ApplicationStatus;
  company: CompanySummary;
  product: ProductSummary;
  requestedAmount: number;
  requestedTermMonths: number;
  purpose: string | null;
  hasCollateral: boolean;
  riskScore: number | null;
  scoringDetails: string | null;
  approvedAmount: number | null;
  approvedTermMonths: number | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ApplicationEvent = {
  timestamp: string;
  eventType: string;
  description: string;
  actor: string;
};

export type OfferType = "REQUESTED" | "LIMIT" | "GUARANTEE" | "COLLATERAL";

export type Offer = {
  id: string;
  amount: number;
  termMonths: number;
  interestRate: number;
  type: OfferType | string;
  monthlyPayment: number;
  overpayment: number;
};

export type Explanation = {
  summary: string;
  reasons: string[];
  nextSteps: string[];
};

export type ScoreFactor = {
  code: string | null;
  name: string;
  contribution: number;
  description: string;
};

export type Score = {
  score: number;
  grade: string;
  riskLevel: "low" | "medium" | "high" | string;
  factors: ScoreFactor[];
};

export type ApplicationDocument = {
  id: string;
  originalFileName: string;
  contentType: string;
  sizeBytes: number | null;
  sha256: string | null;
  uploadDate: string;
  downloadUrl: string;
};

export type StatusUpdate = {
  status: ApplicationStatus | "SCORING_COMPLETED" | string;
  message: string;
};

export type PendingApplication = {
  id: string;
  company: CompanySummary;
  amount: number;
  score: number | null;
  grade: string | null;
  reason: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ManagerDecisionType = "APPROVE" | "COUNTER_OFFER" | "REQUEST_DOCS" | "REJECT";

export type ManagerDecisionRequest = {
  decisionType: ManagerDecisionType;
  approvedAmount?: number | null;
  approvedTermMonths?: number | null;
  comment: string;
  reasonCode?: string | null;
};

export type DeadLetter = {
  applicationId: string;
  attempts: number;
  lastError: string | null;
  updatedAt: string;
};

export type MessageResponse = {
  message: string;
};
