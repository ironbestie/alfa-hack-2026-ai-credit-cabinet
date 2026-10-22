import { ApiError } from "@/api/http";
import type {
  Application,
  ApplicationEvent,
  ApplicationStatus,
  CompanyLookup,
  CreateApplicationRequest,
  CreditProduct,
  Explanation,
  ManagerDecisionRequest,
  MessageResponse,
  Offer,
  PendingApplication,
  PreapprovedOffer,
  QuizRequest,
} from "@/api/types";
import { companies, products, purposePreference, TEST_INNS, type MockCompany } from "./data";

const delay = (ms = 400) => new Promise((resolve) => setTimeout(resolve, ms));

type StoredApplication = Application & {
  companyId: string;
  productId: string;
  submittedAt: number | null;
  consentsSigned: boolean;
  events: ApplicationEvent[];
};

const store = new Map<string, StoredApplication>();

// Секунды после отправки, на которых заявка переходит в следующий статус
const PIPELINE: { at: number; status: ApplicationStatus; description: string; actor: string }[] = [
  { at: 0, status: "SUBMITTED", description: "Заявка отправлена", actor: "Клиент" },
  { at: 2, status: "ENRICHMENT", description: "Проверяем данные в ЕГРЮЛ, ФНС и БКИ", actor: "Система" },
  { at: 5, status: "SCORING", description: "Оцениваем финансовое положение", actor: "Система" },
];
const DECISION_AT = 8;

function findCompanyById(id: string) {
  return companies.find((c) => c.id === id);
}

function findProduct(id: string) {
  return products.find((p) => p.id === id);
}

function getStored(id: string) {
  const app = store.get(id);
  if (!app) throw new ApiError(404, "Заявка не найдена");
  advance(app);
  return app;
}

function pushEvent(app: StoredApplication, status: ApplicationStatus, description: string, actor: string, at = Date.now()) {
  app.status = status;
  app.events.push({ timestamp: new Date(at).toISOString(), eventType: status, description, actor });
}

function advance(app: StoredApplication) {
  if (app.submittedAt === null) return;
  const elapsed = (Date.now() - app.submittedAt) / 1000;
  const processing: ApplicationStatus[] = ["DRAFT", "SUBMITTED", "ENRICHMENT", "SCORING"];
  if (!processing.includes(app.status)) return;

  for (const step of PIPELINE) {
    const reached = app.events.some((e) => e.eventType === step.status);
    if (elapsed >= step.at && !reached) {
      pushEvent(app, step.status, step.description, step.actor, app.submittedAt + step.at * 1000);
    }
  }

  if (elapsed >= DECISION_AT) decide(app, app.submittedAt + DECISION_AT * 1000);
}

function decide(app: StoredApplication, at: number) {
  const company = findCompanyById(app.companyId)!;
  switch (company.outcome) {
    case "APPROVED":
      app.riskScore = 742;
      app.approvedAmount = app.requestedAmount;
      pushEvent(app, "APPROVED", "Заявка одобрена автоматически", "Система", at);
      break;
    case "COUNTER_OFFER":
      app.riskScore = 618;
      app.approvedAmount = Math.min(app.requestedAmount, 3_000_000);
      app.scoringDetails = "Долговая нагрузка 62%";
      pushEvent(app, "COUNTER_OFFER", "Подготовили встречное предложение", "Система", at);
      break;
    case "MANUAL_REVIEW":
      app.riskScore = 655;
      pushEvent(app, "MANUAL_REVIEW", "Заявку смотрит специалист Анна К.", "Система", at);
      break;
    case "REJECTED":
      app.riskScore = 480;
      app.rejectionReason = "Компания работает меньше года и есть налоговая задолженность";
      pushEvent(app, "REJECTED", "Сейчас не можем одобрить кредит", "Система", at);
      break;
  }
}

function toResponse(app: StoredApplication): Application {
  return {
    id: app.id,
    status: app.status,
    requestedAmount: app.requestedAmount,
    requestedTermMonths: app.requestedTermMonths,
    riskScore: app.riskScore,
    scoringDetails: app.scoringDetails,
    approvedAmount: app.approvedAmount,
    rejectionReason: app.rejectionReason,
  };
}

function publicCompany(c: MockCompany): CompanyLookup {
  return {
    id: c.id,
    inn: c.inn,
    name: c.name,
    ogrn: c.ogrn,
    directorName: c.directorName,
    registrationDate: c.registrationDate,
    okved: c.okved,
    okvedDescription: c.okvedDescription,
    category: c.category,
    status: c.status,
  };
}

export const mockCompanies = {
  async lookup(inn: string): Promise<CompanyLookup> {
    await delay(800);
    if (inn === TEST_INNS.egrulDown) throw new ApiError(503, "Сервис ФНС не отвечает");
    const company = companies.find((c) => c.inn === inn);
    if (!company) throw new ApiError(404, "Компания с таким ИНН не найдена");
    return publicCompany(company);
  },
};

export const mockOffers = {
  async preapproved(inn: string): Promise<PreapprovedOffer> {
    await delay(600);
    const company = companies.find((c) => c.inn === inn);
    if (!company) throw new ApiError(400, "Company not found");
    const maxAmount = Math.max(500_000, Math.min(company.annualRevenue / 4, 30_000_000));
    return { inn, maxAmount, termMonths: 24, interestRate: 22, confidenceLevel: "PREAPPROVED" };
  },
};

export const mockQuiz = {
  async recommend({ purpose, amount, termMonths }: QuizRequest): Promise<CreditProduct[]> {
    await delay(500);
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
    if (!findCompanyById(req.companyId)) throw new ApiError(400, "Компания не найдена");
    if (!findProduct(req.productId)) throw new ApiError(400, "Продукт не найден");

    const app: StoredApplication = {
      id: crypto.randomUUID(),
      status: "DRAFT",
      requestedAmount: req.requestedAmount,
      requestedTermMonths: req.requestedTermMonths,
      riskScore: null,
      scoringDetails: null,
      approvedAmount: null,
      rejectionReason: null,
      companyId: req.companyId,
      productId: req.productId,
      submittedAt: null,
      consentsSigned: false,
      events: [],
    };
    pushEvent(app, "DRAFT", "Черновик заявки создан", "Клиент");
    store.set(app.id, app);
    return toResponse(app);
  },

  async list(): Promise<Application[]> {
    await delay(300);
    return [...store.values()].map((app) => {
      advance(app);
      return toResponse(app);
    });
  },

  async get(id: string): Promise<Application> {
    await delay(200);
    return toResponse(getStored(id));
  },

  async signConsents(id: string): Promise<MessageResponse> {
    await delay(300);
    getStored(id).consentsSigned = true;
    return { message: "Consents signed" };
  },

  async submit(id: string): Promise<MessageResponse> {
    await delay();
    const app = getStored(id);
    if (!app.consentsSigned) throw new ApiError(400, "Нужно подписать согласия");
    if (app.status !== "DRAFT") throw new ApiError(409, "Заявка уже отправлена");
    app.submittedAt = Date.now();
    advance(app);
    return { message: "Application submitted successfully" };
  },

  async acceptOffer(id: string): Promise<MessageResponse> {
    await delay();
    const app = getStored(id);
    if (app.status !== "APPROVED" && app.status !== "COUNTER_OFFER") {
      throw new ApiError(409, "Предложение уже недоступно");
    }
    pushEvent(app, "SIGNING", "Предложение принято, осталось подписать документы", "Клиент");
    return { message: "Offer accepted" };
  },

  async sign(id: string): Promise<MessageResponse> {
    await delay(800);
    const app = getStored(id);
    if (app.status !== "SIGNING") throw new ApiError(409, "Сначала примите предложение");
    pushEvent(app, "FUNDED", "Деньги отправлены на счёт", "Система");
    return { message: "Documents signed, funds disbursed" };
  },

  async uploadDocument(id: string, file: File): Promise<MessageResponse> {
    await delay(700);
    const app = getStored(id);
    app.events.push({
      timestamp: new Date().toISOString(),
      eventType: "DOCUMENT_UPLOADED",
      description: `Загружен документ ${file.name}`,
      actor: "Клиент",
    });
    if (app.status === "NEEDS_MORE_INFO") {
      app.submittedAt = Date.now();
      pushEvent(app, "SUBMITTED", "Заявка вернулась на проверку", "Система");
    }
    return { message: "Document uploaded" };
  },

  async timeline(id: string): Promise<ApplicationEvent[]> {
    await delay(200);
    return [...getStored(id).events];
  },

  async offers(id: string): Promise<Offer[]> {
    await delay(300);
    const app = getStored(id);
    const product = findProduct(app.productId)!;
    const list: Offer[] = [];
    if (app.approvedAmount !== null) {
      list.push({
        id: app.id,
        amount: app.approvedAmount,
        termMonths: app.requestedTermMonths,
        interestRate: product.baseRate,
        type: app.status === "COUNTER_OFFER" ? "COUNTER_OFFER" : "STANDARD",
      });
    }
    if (app.status === "REJECTED" || app.status === "COUNTER_OFFER") {
      list.push({
        id: crypto.randomUUID(),
        amount: Math.round(app.requestedAmount * 0.6),
        termMonths: Math.min(app.requestedTermMonths + 12, 36),
        interestRate: 19.5,
        type: "STATE_GUARANTEE",
      });
    }
    return list;
  },

  async explanation(id: string): Promise<Explanation> {
    await delay(500);
    const app = getStored(id);
    if (app.status === "COUNTER_OFFER") {
      return {
        summary: `Мы готовы одобрить ${app.approvedAmount?.toLocaleString("ru-RU")} ₽ вместо ${app.requestedAmount.toLocaleString("ru-RU")} ₽.`,
        reasons: ["Сейчас на платежи по кредитам уходит 62% свободных денег бизнеса."],
        nextSteps: ["Погасите мелкие кредиты, и лимит может вырасти.", "Вернитесь через 3 месяца."],
      };
    }
    if (app.status === "REJECTED") {
      return {
        summary: "Сейчас мы не можем выдать кредит на запрошенных условиях.",
        reasons: ["Компания зарегистрирована меньше года назад.", "Есть задолженность по налогам 150 000 ₽."],
        nextSteps: ["Погасите налоговую задолженность.", "Посмотрите кредит с господдержкой: там мягче требования к сроку работы."],
      };
    }
    return {
      summary: "Заявка одобрена на запрошенных условиях.",
      reasons: ["Стабильная выручка и низкая долговая нагрузка."],
      nextSteps: ["Подпишите документы, и деньги придут на счёт в течение дня."],
    };
  },
};

export const mockManager = {
  async pending(): Promise<PendingApplication[]> {
    await delay(300);
    return [...store.values()]
      .map((app) => {
        advance(app);
        return app;
      })
      .filter((app) => app.status === "MANUAL_REVIEW")
      .map((app) => ({ id: app.id, amount: app.requestedAmount, score: app.riskScore, reason: app.scoringDetails }));
  },

  async decide(id: string, req: ManagerDecisionRequest): Promise<MessageResponse> {
    await delay();
    const app = getStored(id);
    if (app.status !== "MANUAL_REVIEW") throw new ApiError(409, "Заявка уже обработана");
    switch (req.decisionType) {
      case "APPROVE":
        app.approvedAmount = req.approvedAmount ?? app.requestedAmount;
        pushEvent(app, "APPROVED", "Одобрено специалистом", "Менеджер");
        break;
      case "COUNTER_OFFER":
        app.approvedAmount = req.approvedAmount;
        pushEvent(app, "COUNTER_OFFER", "Специалист предложил другие условия", "Менеджер");
        break;
      case "REQUEST_DOCUMENTS":
        pushEvent(app, "NEEDS_MORE_INFO", req.comment || "Нужен дополнительный документ", "Менеджер");
        break;
      case "REJECT":
        app.rejectionReason = req.comment;
        pushEvent(app, "REJECTED", "Отказ специалиста", "Менеджер");
        break;
    }
    return { message: "Decision processed" };
  },
};
