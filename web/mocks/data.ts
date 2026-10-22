import type { CompanyLookup, CreditProduct, LoanPurpose } from "@/api/types";

export type MockOutcome = "APPROVED" | "COUNTER_OFFER" | "REJECTED" | "MANUAL_REVIEW";

export type MockCompany = CompanyLookup & {
  id: string;
  annualRevenue: number;
  totalDebt: number;
  taxDebt: number;
  outcome: MockOutcome;
};

export const TEST_INNS = {
  approved: "7707083893",
  counterOffer: "7712345671",
  rejected: "7710140679",
  liquidated: "7720000007",
  egrulDown: "7730000006",
} as const;

export const companies: MockCompany[] = [
  {
    id: "5b0d6c1e-7f3a-4f7e-9d43-1a2b3c4d5e01",
    inn: TEST_INNS.approved,
    ogrn: "1027700132195",
    name: "ООО «ТехноПрогресс»",
    okved: "62.01",
    okvedDescription: "Разработка компьютерного программного обеспечения",
    directorName: "Иванов Иван Иванович",
    registrationDate: "2018-03-15",
    category: "Малое предприятие",
    status: "active",
    annualRevenue: 50_000_000,
    totalDebt: 5_000_000,
    taxDebt: 0,
    outcome: "APPROVED",
  },
  {
    id: "5b0d6c1e-7f3a-4f7e-9d43-1a2b3c4d5e02",
    inn: TEST_INNS.counterOffer,
    ogrn: "1167746123456",
    name: "ООО «Хлебный двор»",
    okved: "10.71",
    okvedDescription: "Производство хлеба и хлебобулочных изделий",
    directorName: "Ковалёва Мария Сергеевна",
    registrationDate: "2022-03-12",
    category: "Микропредприятие",
    status: "active",
    annualRevenue: 15_000_000,
    totalDebt: 9_300_000,
    taxDebt: 0,
    outcome: "COUNTER_OFFER",
  },
  {
    id: "5b0d6c1e-7f3a-4f7e-9d43-1a2b3c4d5e03",
    inn: TEST_INNS.rejected,
    ogrn: "1157746123456",
    name: "ООО «СтройМаркет»",
    okved: "41.20",
    okvedDescription: "Строительство жилых и нежилых зданий",
    directorName: "Петров Пётр Петрович",
    registrationDate: "2025-11-01",
    category: "Микропредприятие",
    status: "active",
    annualRevenue: 5_000_000,
    totalDebt: 4_000_000,
    taxDebt: 150_000,
    outcome: "REJECTED",
  },
  {
    id: "5b0d6c1e-7f3a-4f7e-9d43-1a2b3c4d5e04",
    inn: TEST_INNS.liquidated,
    ogrn: "1107746000001",
    name: "ООО «Закрытый проект»",
    okved: "47.19",
    okvedDescription: "Прочая розничная торговля",
    directorName: "Сидоров Сергей Олегович",
    registrationDate: "2010-06-01",
    category: "Микропредприятие",
    status: "liquidated",
    annualRevenue: 0,
    totalDebt: 0,
    taxDebt: 0,
    outcome: "REJECTED",
  },
];

export const products: CreditProduct[] = [
  {
    id: "8e1f2a3b-0000-4000-8000-000000000001",
    name: "Оборотный кредит",
    description: "Лучше всего под закупку товара и пополнение оборотных средств",
    minAmount: 100_000,
    maxAmount: 30_000_000,
    minTerm: 3,
    maxTerm: 36,
    baseRate: 22,
    tags: '["Без залога", "Онлайн"]',
  },
  {
    id: "8e1f2a3b-0000-4000-8000-000000000002",
    name: "Овердрафт",
    description: "Для краткосрочных кассовых разрывов",
    minAmount: 100_000,
    maxAmount: 5_000_000,
    minTerm: 1,
    maxTerm: 12,
    baseRate: 24,
    tags: '["Без залога", "Возобновляемый"]',
  },
  {
    id: "8e1f2a3b-0000-4000-8000-000000000003",
    name: "Инвестиционный кредит",
    description: "На покупку оборудования и транспорта",
    minAmount: 500_000,
    maxAmount: 100_000_000,
    minTerm: 12,
    maxTerm: 84,
    baseRate: 20,
    tags: '["С залогом"]',
  },
  {
    id: "8e1f2a3b-0000-4000-8000-000000000004",
    name: "Кредит с господдержкой",
    description: "Поручительство Корпорации МСП",
    minAmount: 100_000,
    maxAmount: 50_000_000,
    minTerm: 3,
    maxTerm: 36,
    baseRate: 19.5,
    tags: '["Без залога", "Госпрограмма"]',
  },
];

export const purposePreference: Partial<Record<LoanPurpose, string>> = {
  ЗАКУПКА: "Оборотный",
  ОБОРОТ: "Оборотный",
  ОБОРУДОВАНИЕ: "Инвестиционный",
  ГОСКОНТРАКТ: "господдержкой",
  РАЗРЫВ: "Овердрафт",
};
