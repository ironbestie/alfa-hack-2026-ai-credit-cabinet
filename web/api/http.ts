import { getRole, type Role } from "@/lib/session";

export const USE_MOCKS = process.env.NEXT_PUBLIC_USE_MOCKS !== "false";

type AuthRole = Role | "public";

const credentials: Record<AuthRole, string | undefined> = {
  client: process.env.NEXT_PUBLIC_CLIENT_AUTH ?? "client:password",
  manager: process.env.NEXT_PUBLIC_MANAGER_AUTH ?? "manager:password",
  public: undefined,
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code: string | null = null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const fallbackMessages: Record<number, string> = {
  400: "Проверьте введённые данные",
  401: "Нужно войти в кабинет",
  403: "Нет доступа к этому разделу",
  404: "Не нашли данные",
  409: "Заявка уже в другом статусе, обновите страницу",
  429: "Слишком много запросов, подождите минуту",
  500: "Сервис временно недоступен, попробуйте позже",
};

const codeMessages: Record<string, string> = {
  FILE_TOO_LARGE: "Файл больше 10 МБ",
  INVALID_MULTIPART: "Не получилось загрузить файл, попробуйте ещё раз",
  CONFLICT: "Заявку уже изменили, обновите страницу",
};

type RequestOptions = {
  method?: "GET" | "POST";
  body?: unknown;
  role?: AuthRole;
  idempotencyKey?: string;
  query?: Record<string, string | number | undefined>;
};

export function authHeader(role: AuthRole = getRole() ?? "client") {
  const auth = credentials[role];
  return auth ? `Basic ${btoa(auth)}` : null;
}

function buildUrl(path: string, query?: RequestOptions["query"]) {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

async function send(path: string, options: RequestOptions, accept: string) {
  const { method = "GET", body, role, idempotencyKey, query } = options;
  const headers = new Headers({ Accept: accept });

  const auth = authHeader(role);
  if (auth) headers.set("Authorization", auth);
  if (idempotencyKey) headers.set("Idempotency-Key", idempotencyKey);

  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers.set("Content-Type", "application/json");
    payload = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), { method, headers, body: payload });
  } catch {
    throw new ApiError(0, "Нет связи с сервером, проверьте соединение");
  }

  if (!response.ok) throw await toApiError(response);
  return response;
}

async function toApiError(response: Response) {
  const text = await response.text();
  const data = text ? safeJson(text) : null;

  let serverMessage: string | null = null;
  let code: string | null = null;
  if (data && typeof data === "object") {
    if ("error" in data && data.error) serverMessage = String(data.error);
    if ("code" in data && data.code) code = String(data.code);
  }

  const message =
    (code && codeMessages[code]) ||
    serverMessage ||
    fallbackMessages[response.status] ||
    "Что-то пошло не так, попробуйте ещё раз";
  return new ApiError(response.status, message, code);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options, "application/json");
  const text = await response.text();
  return (text ? safeJson(text) : null) as T;
}

export async function requestBlob(path: string, options: RequestOptions = {}) {
  const response = await send(path, options, "*/*");
  return response.blob();
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
