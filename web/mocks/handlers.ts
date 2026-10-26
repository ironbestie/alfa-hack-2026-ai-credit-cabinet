import { ApiError } from "@/api/http";
import type {
  Application,
  ApplicationDocument,
  ApplicationEvent,
  ApplicationStatus,
  Company,
  CompanyLookup,
  CreateApplicationRequest,
  CreditProduct,
  DeadLetter,
  Explanation,
  ManagerDecisionRequest,
  MessageResponse,
  Offer,
  Page,
  PageParams,
  PendingApplication,
  PreapprovedOffer,
  QuizRequest,
  Score,
  ScoreFactor,
} from "@/api/types";
import { calculateLoan } from "@/lib/credit";
import { companies, products, purposePreference, TEST_INNS, type MockCompany } from "./data";

const delay = (ms = 350) => new Promise((resolve) => setTimeout(resolve, ms));

type StoredApplication = {
  id: string;
  status: ApplicationStatus;
  companyId: string;
  productId: string;
  requestedAmount: number;
  requestedTermMonths: number;
  purpose: string | null;
  hasCollateral: boolean;
  riskScore: number | null;
  approvedAmount: number | null;
  approvedTermMonths: number | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
  submittedAt: number | null;
  consentsSigned: boolean;
  offers: Offer[];
  events: ApplicationEvent[];
  documents: ApplicationDocument[];
};

const STORE_KEY = "credit-cabinet:mock-store";
let memoryStore: Record<string, StoredApplication> | null = null;

function load(): Record<string, StoredApplication> {
  if (memoryStore && typeof window === "undefined") return memoryStore;
  try {
    const raw = window.localStorage.getItem(STORE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {}
  return memoryStore ?? seed();
}

function save(store: Record<string, StoredApplication>) {
  memoryStore = store;
  try {
    window.localStorage.setItem(STORE_KEY, JSON.stringify(store));
  } catch {}
}

function mutate<T>(fn: (store: Record<string, StoredApplication>) => T): T {
  const store = load();
  Object.values(store).forEach(advance);
  const result = fn(store);
  save(store);
  return result;
}

function seed() {
  const store: Record<string, StoredApplication> = {};
  const now = Date.now();
  const funded = draft(companyByInn(TEST_INNS.approved)!, products[0], 10_000_000, 24, now - 86_400_000);
  funded.status = "FUNDED";
  funded.riskScore = 800;
  funded.approvedAmount = 10_000_000;
  funded.approvedTermMonths = 24;
  log(funded, "DECISION_MADE", "Кредит одобрен", "system", now - 86_000_000);
  log(funded, "STATUS_CHANGED", "Деньги выданы", "system", now - 85_000_000);
  store[funded.id] = funded;

  const review = draft(companyByInn(TEST_INNS.manualReview)!, products[0], 5_000_000, 18, now - 3_600_000);
  review.status = "MANUAL_REVIEW";
  review.riskScore = 655;
  review.consentsSigned = true;
  log(review, "STATUS_CHANGED", "БКИ не ответило вовремя, заявка передана специалисту", "system", now - 3_500_000);
  store[review.id] = review;
  return store;
}

function companyByInn(inn: string) {
  return companies.find((c) => c.inn === inn);
}

function companyById(id: string) {
  return companies.find((c) => c.id === id);
}

function productById(id: string) {
  return products.find((p) => p.id === id);
}

function draft(
  company: MockCompany,
  product: CreditProduct,
  amount: number,
  term: number,
  at = Date.now(),
  purpose: string | null = "Пополнение оборотных средств",
  hasCollateral = false,
): StoredApplication {
  const iso = new Date(at).toISOString();
  const app: StoredApplication = {
    id: crypto.randomUUID(),
    status: "DRAFT",
    companyId: company.id,
    productId: product.id,
    requestedAmount: amount,
    requestedTermMonths: term,
    purpose,
    hasCollateral,
    riskScore: null,
    approvedAmount: null,
    approvedTermMonths: null,
    rejectionReason: null,
    createdAt: iso,
    updatedAt: iso,
    submittedAt: null,
    consentsSigned: false,
    offers: [],
    events: [],
    documents: [],
  };
  log(app, "APPLICATION_CREATED", "Заявка создана", "client", at);
  return app;
}

function log(app: StoredApplication, eventType: string, description: string, actor: string, at = Date.now()) {
  const timestamp = new Date(at).toISOString();
  app.events.push({ timestamp, eventType, description, actor });
  app.updatedAt = timestamp;
}

function setStatus(app: StoredApplication, status: ApplicationStatus, description: string, actor = "system", at = Date.now()) {
  app.status = status;
  log(app, "STATUS_CHANGED", description, actor, at);
}

function offer(type: string, amount: number, termMonths: number, interestRate: number): Offer {
  const { payment, overpayment } = calculateLoan(amount, interestRate, termMonths);
  return { id: crypto.randomUUID(), amount, termMonths, interestRate, type, monthlyPayment: payment, overpayment };
}

const STAGES: { at: number; status: ApplicationStatus; description: string }[] = [
  { at: 0, status: "ENRICHMENT", description: "Собираем данные из ЕГРЮЛ, ФНС и БКИ" },
  { at: 2, status: "SCORING", description: "Оцениваем финансовое положение" },
  { at: 4, status: "DECISION", description: "Принимаем решение" },
];

function advance(app: StoredApplication) {
  if (app.submittedAt === null) return;
  if (!["DRAFT", "ENRICHMENT", "SCORING", "DECISION"].includes(app.status)) return;

  const company = companyById(app.companyId)!;
  const elapsed = (Date.now() - app.submittedAt) / 1000;
  const decisionAt = company.outcome === "FNS_RETRY" ? 10 : 6;

  for (const stage of STAGES) {
    const reached = app.events.some((e) => e.description === stage.description);
    if (elapsed >= stage.at && !reached) {
      setStatus(app, stage.status, stage.description, "system", app.submittedAt + stage.at * 1000);
      if (stage.status === "ENRICHMENT" && company.outcome === "FNS_RETRY") {
        log(app, "DATA_ENRICHED", "ФНС временно недоступна, повторим запрос автоматически", "system", app.submittedAt + 500);
      }
    }
  }

  if (elapsed >= decisionAt) decide(app, company, app.submittedAt + decisionAt * 1000);
}

function decide(app: StoredApplication, company: MockCompany, at: number) {
  const rate = productById(app.productId)!.baseRate;
  const amount = app.requestedAmount;
  const term = app.requestedTermMonths;
  log(app, "SCORING_COMPLETED", "Оценка завершена", "system", at - 500);

  switch (company.outcome) {
    case "APPROVED":
    case "FNS_RETRY":
      app.riskScore = company.outcome === "APPROVED" ? 800 : 720;
      app.approvedAmount = amount;
      app.approvedTermMonths = term;
      app.offers = [offer("REQUESTED", amount, term, rate)];
      setStatus(app, "APPROVED", "Кредит одобрен", "system", at);
      break;
    case "COUNTER_OFFER": {
      const limit = Math.min(amount, 3_000_000);
      app.riskScore = 610;
      app.approvedAmount = limit;
      app.approvedTermMonths = term;
      app.offers = [
        offer("LIMIT", limit, term, rate + 1),
        offer("GUARANTEE", amount, term, 19.5),
        offer("COLLATERAL", amount, Math.min(term + 12, 36), rate - 2),
      ];
      setStatus(app, "COUNTER_OFFER", "Подготовили встречные предложения", "system", at);
      break;
    }
    case "MANUAL_REVIEW":
      app.riskScore = 655;
      setStatus(app, "MANUAL_REVIEW", "БКИ не ответило вовремя, заявка передана специалисту", "system", at);
      break;
    case "REJECTED":
      app.riskScore = 430;
      app.rejectionReason = "Высокая долговая нагрузка и налоговая задолженность";
      setStatus(app, "REJECTED", "В кредите отказано", "system", at);
      break;
    case "LIQUIDATED":
      app.rejectionReason = "Компания ликвидирована или находится в процессе ликвидации";
      setStatus(app, "REJECTED", "В кредите отказано: компания ликвидирована", "system", at);
      break;
  }
  log(app, "DECISION_MADE", "Решение принято", "system", at);
}

function toResponse(app: StoredApplication): Application {
  const company = companyById(app.companyId)!;
  const product = productById(app.productId)!;
  return {
    id: app.id,
    status: app.status,
    company: {
      id: company.id,
      inn: company.inn,
      ogrn: company.ogrn,
      name: company.name,
      category: company.category,
      status: company.status,
    },
    product: { id: product.id, name: product.name, baseRate: product.baseRate },
    requestedAmount: app.requestedAmount,
    requestedTermMonths: app.requestedTermMonths,
    purpose: app.purpose,
    hasCollateral: app.hasCollateral,
    riskScore: app.riskScore,
    scoringDetails: null,
    approvedAmount: app.approvedAmount,
    approvedTermMonths: app.approvedTermMonths,
    rejectionReason: app.rejectionReason,
    createdAt: app.createdAt,
    updatedAt: app.updatedAt,
  };
}

function paginate<T>(items: T[], { page = 0, size = 20 }: PageParams = {}): Page<T> {
  const start = page * size;
  return {
    items: items.slice(start, start + size),
    page,
    size,
    totalItems: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}

function find(store: Record<string, StoredApplication>, id: string) {
  const app = store[id];
  if (!app) throw new ApiError(404, "Заявка не найдена", "NOT_FOUND");
  return app;
}

function invalidTransition(): never {
  throw new ApiError(409, "Действие недоступно в текущем статусе заявки", "INVALID_STATE_TRANSITION");
}

function toCompany(c: MockCompany): Company {
  const { outcome, ...company } = c;
  void outcome;
  return company;
}

const byUpdatedDesc = (a: StoredApplication, b: StoredApplication) => b.updatedAt.localeCompare(a.updatedAt);

export const mockCompanies = {
  async list(params?: PageParams): Promise<Page<Company>> {
    await delay(200);
    return paginate(companies.map(toCompany), params);
  },

  async lookup(inn: string): Promise<CompanyLookup> {
    await delay(700);
    const company = companyByInn(inn);
    if (!company) throw new ApiError(404, "Компания с таким ИНН не найдена", "NOT_FOUND");
    return {
      inn: company.inn,
      name: company.name,
      ogrn: company.ogrn,
      directorName: company.directorName,
      registrationDate: company.registrationDate,
      okved: `${company.okved} (${company.okvedDescription})`,
    };
  },
};

export const mockProducts = {
  async list(params?: PageParams): Promise<Page<CreditProduct>> {
    await delay(200);
    return paginate(products, params);
  },
};

export const mockOffers = {
  async preapproved(inn: string): Promise<PreapprovedOffer> {
    await delay(500);
    const company = companyByInn(inn);
    if (!company) throw new ApiError(404, "Компания не найдена", "NOT_FOUND");
    const maxAmount = Math.max(500_000, Math.min(company.annualRevenue / 4, 30_000_000));
    const { payment, overpayment } = calculateLoan(maxAmount, 22, 24);
    return {
      inn,
      maxAmount,
      termMonths: 24,
      interestRate: 22,
      monthlyPayment: payment,
      overpayment,
      confidenceLevel: "PREAPPROVED",
    };
  },
};

export const mockQuiz = {
  async recommend({ purpose, amount, termMonths }: QuizRequest): Promise<CreditProduct[]> {
    await delay(400);
    const preferred = purposePreference[purpose];
    return products
      .filter((p) => amount >= p.minAmount && amount <= p.maxAmount && termMonths >= p.minTerm && termMonths <= p.maxTerm)
      .sort((a, b) => {
        const score = (p: CreditProduct) => (preferred && p.name.includes(preferred) ? 100 : -p.baseRate);
        return score(b) - score(a);
      })
      .slice(0, 2);
  },
};

export const mockApplications = {
  async create(req: CreateApplicationRequest): Promise<Application> {
    await delay();
    const company = companyById(req.companyId);
    const product = productById(req.productId);
    if (!company || !product) throw new ApiError(404, "Компания или продукт не найдены", "NOT_FOUND");
    if (
      req.requestedAmount < product.minAmount ||
      req.requestedAmount > product.maxAmount ||
      req.requestedTermMonths < product.minTerm ||
      req.requestedTermMonths > product.maxTerm
    ) {
      throw new ApiError(400, "Сумма или срок вне лимитов продукта", "BAD_REQUEST");
    }
    return mutate((store) => {
      const app = draft(company, product, req.requestedAmount, req.requestedTermMonths, Date.now(), req.purpose, req.hasCollateral);
      store[app.id] = app;
      return toResponse(app);
    });
  },

  async list(params?: PageParams): Promise<Page<Application>> {
    await delay(250);
    return mutate((store) => paginate(Object.values(store).sort(byUpdatedDesc).map(toResponse), params));
  },

  async get(id: string): Promise<Application> {
    await delay(150);
    return mutate((store) => toResponse(find(store, id)));
  },

  async signConsents(id: string): Promise<MessageResponse> {
    await delay(250);
    return mutate((store) => {
      const app = find(store, id);
      if (!app.consentsSigned) {
        app.consentsSigned = true;
        log(app, "CONSENT_SIGNED", "Подписаны согласия на запрос в БКИ и обработку данных", "client");
      }
      return { message: "Consents signed" };
    });
  },

  async submit(id: string): Promise<MessageResponse> {
    await delay();
    return mutate((store) => {
      const app = find(store, id);
      if (app.status !== "DRAFT") invalidTransition();
      if (!app.consentsSigned) throw new ApiError(409, "Сначала подпишите согласия", "ILLEGAL_STATE");
      app.submittedAt = Date.now();
      advance(app);
      return { message: "Application submitted successfully" };
    });
  },

  async acceptOffer(id: string, offerId?: string): Promise<MessageResponse> {
    await delay();
    return mutate((store) => {
      const app = find(store, id);
      if (app.status !== "APPROVED" && app.status !== "COUNTER_OFFER") invalidTransition();
      const chosen = offerId ? app.offers.find((o) => o.id === offerId) : app.offers[0];
      if (!chosen) throw new ApiError(404, "Предложение не найдено", "NOT_FOUND");
      app.approvedAmount = chosen.amount;
      app.approvedTermMonths = chosen.termMonths;
      setStatus(app, "SIGNING", "Предложение принято, осталось подписать документы", "client");
      return { message: "Offer accepted" };
    });
  },

  async sign(id: string): Promise<MessageResponse> {
    await delay(900);
    return mutate((store) => {
      const app = find(store, id);
      if (app.status !== "SIGNING") invalidTransition();
      setStatus(app, "FUNDED", "Документы подписаны, деньги отправлены на счёт", "client");
      return { message: "Documents signed, funds disbursed" };
    });
  },

  async uploadDocument(id: string, file: File): Promise<MessageResponse> {
    await delay(700);
    return mutate((store) => {
      const app = find(store, id);
      if (app.status !== "NEEDS_MORE_INFO") invalidTransition();
      const docId = crypto.randomUUID();
      app.documents.push({
        id: docId,
        originalFileName: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
        sha256: null,
        uploadDate: new Date().toISOString(),
        downloadUrl: `/api/applications/${id}/documents/${docId}/download`,
      });
      log(app, "DOCUMENT_UPLOADED", `Загружен документ ${file.name}`, "client");
      setStatus(app, "MANUAL_REVIEW", "Документ получен, заявка вернулась к специалисту", "system");
      return { message: "Document uploaded" };
    });
  },

  async documents(id: string): Promise<ApplicationDocument[]> {
    await delay(200);
    return mutate((store) => [...find(store, id).documents]);
  },

  async downloadDocument(id: string, documentId: string): Promise<Blob> {
    await delay(200);
    return mutate((store) => {
      const doc = find(store, id).documents.find((d) => d.id === documentId);
      if (!doc) throw new ApiError(404, "Документ не найден", "NOT_FOUND");
      return new Blob([`Демо-файл ${doc.originalFileName}`], { type: "text/plain" });
    });
  },

  async timeline(id: string, params?: PageParams): Promise<Page<ApplicationEvent>> {
    await delay(150);
    return mutate((store) => paginate([...find(store, id).events], params));
  },

  async offers(id: string): Promise<Offer[]> {
    await delay(250);
    return mutate((store) => [...find(store, id).offers]);
  },

  async score(id: string): Promise<Score> {
    await delay(250);
    return mutate((store) => {
      const app = find(store, id);
      if (app.riskScore === null) throw new ApiError(409, "Оценка ещё не готова", "ILLEGAL_STATE");
      return scoreFor(app.riskScore, companyById(app.companyId)!);
    });
  },

  async explanation(id: string): Promise<Explanation> {
    await delay(400);
    return mutate((store) => explanationFor(find(store, id)));
  },
};

function scoreFor(score: number, company: MockCompany): Score {
  const grade = score >= 750 ? "A" : score >= 680 ? "B" : score >= 600 ? "C" : score >= 500 ? "D" : "E";
  const debtLoad = Math.round((company.totalDebt / Math.max(company.annualRevenue, 1)) * 100);
  const factors: ScoreFactor[] = [
    { code: null, name: "Возраст бизнеса", contribution: company.registrationDate < "2024-01-01" ? 50 : -40, description: company.registrationDate.slice(0, 4) },
    { code: debtLoad > 50 ? "R01" : null, name: "Долговая нагрузка", contribution: debtLoad > 50 ? -80 : 40, description: `${debtLoad}%` },
    { code: company.taxDebt > 0 ? "R03" : null, name: "Налоговая задолженность", contribution: company.taxDebt > 0 ? -60 : 20, description: company.taxDebt > 0 ? `${company.taxDebt.toLocaleString("ru-RU")} ₽` : "нет" },
    { code: null, name: "Клиент банка", contribution: company.bankRelationshipMonths >= 12 ? 30 : 0, description: `${company.bankRelationshipMonths} мес.` },
  ];
  return { score, grade, riskLevel: score >= 680 ? "low" : score >= 550 ? "medium" : "high", factors };
}

function explanationFor(app: StoredApplication): Explanation {
  switch (app.status) {
    case "COUNTER_OFFER":
      return {
        summary: `Мы готовы выдать ${app.approvedAmount?.toLocaleString("ru-RU")} ₽ сразу или всю сумму с поручительством или залогом.`,
        reasons: ["Сейчас на платежи по кредитам уходит около 62% свободных денег бизнеса."],
        nextSteps: ["Выберите подходящее предложение.", "Погасите часть мелких кредитов, и лимит может вырасти."],
      };
    case "REJECTED":
      return {
        summary: "Сейчас мы не можем выдать кредит на запрошенных условиях.",
        reasons: [app.rejectionReason ?? "Высокий риск по заявке."],
        nextSteps: ["Погасите налоговую задолженность.", "Вернитесь через 3 месяца или посмотрите кредит с господдержкой."],
      };
    case "NEEDS_MORE_INFO":
      return {
        summary: "Специалисту нужен дополнительный документ.",
        reasons: ["Нужно подтвердить выручку за последний год."],
        nextSteps: ["Загрузите выписку по счёту в PDF, JPG или PNG до 10 МБ."],
      };
    default:
      return {
        summary: "Заявка одобрена на запрошенных условиях.",
        reasons: ["Стабильная выручка и умеренная долговая нагрузка."],
        nextSteps: ["Примите предложение и подпишите документы."],
      };
  }
}

export const mockManager = {
  async pending(params?: PageParams): Promise<Page<PendingApplication>> {
    await delay(250);
    return mutate((store) =>
      paginate(
        Object.values(store)
          .filter((app) => app.status === "MANUAL_REVIEW")
          .sort((a, b) => a.updatedAt.localeCompare(b.updatedAt))
          .map((app) => {
            const response = toResponse(app);
            return {
              id: app.id,
              company: response.company,
              amount: app.requestedAmount,
              score: app.riskScore,
              grade: app.riskScore ? scoreFor(app.riskScore, companyById(app.companyId)!).grade : null,
              reason: "БКИ не ответило вовремя",
              createdAt: app.createdAt,
              updatedAt: app.updatedAt,
            };
          }),
        params,
      ),
    );
  },

  async decide(id: string, req: ManagerDecisionRequest): Promise<MessageResponse> {
    await delay();
    return mutate((store) => {
      const app = find(store, id);
      if (app.status !== "MANUAL_REVIEW") invalidTransition();
      const amount = req.approvedAmount ?? app.requestedAmount;
      const term = req.approvedTermMonths ?? app.requestedTermMonths;
      const rate = productById(app.productId)!.baseRate;
      switch (req.decisionType) {
        case "APPROVE":
          app.approvedAmount = amount;
          app.approvedTermMonths = term;
          app.offers = [offer("REQUESTED", amount, term, rate)];
          setStatus(app, "APPROVED", req.comment || "Одобрено специалистом", "manager");
          break;
        case "COUNTER_OFFER":
          app.approvedAmount = amount;
          app.approvedTermMonths = term;
          app.offers = [offer("LIMIT", amount, term, rate)];
          setStatus(app, "COUNTER_OFFER", req.comment || "Специалист предложил другие условия", "manager");
          break;
        case "REQUEST_DOCS":
          log(app, "DOCUMENT_REQUESTED", req.comment || "Нужен дополнительный документ", "manager");
          setStatus(app, "NEEDS_MORE_INFO", "Специалист запросил документы", "manager");
          break;
        case "REJECT":
          app.rejectionReason = req.comment || "Отказ специалиста";
          setStatus(app, "REJECTED", "В кредите отказано специалистом", "manager");
          break;
      }
      log(app, "DECISION_MADE", "Решение специалиста", "manager");
      return { message: "Решение обработано" };
    });
  },

  async deadLetters(params?: PageParams): Promise<Page<DeadLetter>> {
    await delay(250);
    return paginate([], params);
  },

  async retry(): Promise<MessageResponse> {
    await delay();
    throw new ApiError(404, "Задача обработки не найдена", "NOT_FOUND");
  },
};
