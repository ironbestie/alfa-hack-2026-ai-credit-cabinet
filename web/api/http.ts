export const USE_MOCKS = process.env.NEXT_PUBLIC_USE_MOCKS !== "false";

export type Role = "client" | "manager" | "public";

const credentials: Record<Role, string | undefined> = {
  client: process.env.NEXT_PUBLIC_CLIENT_AUTH,
  manager: process.env.NEXT_PUBLIC_MANAGER_AUTH,
  public: undefined,
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const fallbackMessages: Record<number, string> = {
  401: "Нужно войти в кабинет",
  403: "Нет доступа к этому разделу",
  404: "Не нашли данные",
  409: "Заявка уже в другом статусе, обновите страницу",
  429: "Слишком много запросов, попробуйте через минуту",
};

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  role?: Role;
  idempotencyKey?: string;
};

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, role = "client", idempotencyKey } = options;
  const headers = new Headers({ Accept: "application/json" });

  const auth = credentials[role];
  if (auth) headers.set("Authorization", `Basic ${btoa(auth)}`);
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
    response = await fetch(path, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, "Сервис временно недоступен, проверьте соединение");
  }

  const text = await response.text();
  const data = text ? safeJson(text) : null;

  if (!response.ok) {
    const serverMessage =
      data && typeof data === "object" && "error" in data && data.error ? String(data.error) : null;
    const message =
      serverMessage || fallbackMessages[response.status] || "Что-то пошло не так, попробуйте ещё раз";
    throw new ApiError(response.status, message);
  }

  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
