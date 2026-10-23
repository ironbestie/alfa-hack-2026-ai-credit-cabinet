# Умный кредитный кабинет предпринимателя

Backend кредитного конвейера для малого и микробизнеса: заявка, сбор данных, скоринг,
решение, контрпредложения, ручное рассмотрение и имитация выдачи.

## Стек

- Kotlin 2.1 / Java 21 / Spring Boot 3.5
- PostgreSQL 16, Redis 7, MinIO (S3)
- Spring Modulith Events, Spring Security (Basic Auth)
- WireMock для внешних моков (ЕГРЮЛ, ФНС, БКИ, банк)
- Testcontainers для интеграционного теста контекста

## Быстрый запуск

1. Поднять инфраструктуру:

```bash
docker compose up -d postgres redis mocks minio
```

2. Запустить приложение локально:

```bash
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html

Dockerfile и Flyway-миграции добавляются отдельно. Если PostgreSQL уже создан старой
версией приложения, для событий `CONSENT_SIGNED` и `DOCUMENT_UPLOADED` потребуется
обновить check-constraint `application_events_event_type_check`.

## Авторизация

Basic Auth:

| Логин | Пароль | Роль |
|---|---|---|
| `client` | `password` | CLIENT |
| `manager` | `password` | MANAGER |

Клиент видит только свои заявки, менеджер — все. SMS/OTP и вход по телефону
намеренно не реализованы: по условиям кейса способ авторизации выбирает команда.

## Основные эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/api/companies` | Список компаний (нужен `companyId`) |
| `GET` | `/api/companies/lookup?inn=` | Карточка компании из БД или ЕГРЮЛ-мока |
| `GET` | `/api/products` | Кредитные продукты (нужен `productId`) |
| `POST` | `/api/applications` | Создать черновик заявки |
| `POST` | `/api/applications/{id}/consents` | Подписать согласия БКИ и ПДн |
| `POST` | `/api/applications/{id}/submit` | Отправить заявку в конвейер |
| `GET` | `/api/applications/{id}` | Карточка заявки |
| `GET` | `/api/applications/{id}/timeline` | История событий |
| `GET` | `/api/applications/{id}/stream` | SSE-поток статусов |
| `GET` | `/api/applications/{id}/score` | Балл, грейд и факторы |
| `GET` | `/api/applications/{id}/offers` | Предложения и контрпредложения |
| `GET` | `/api/applications/{id}/explanation` | Объяснение решения |
| `POST` | `/api/applications/{id}/accept-offer` | Принять предложение |
| `POST` | `/api/applications/{id}/sign` | Имитация подписания и выдачи |
| `GET` | `/api/manager/applications/pending` | Очередь ручного рассмотрения |
| `POST` | `/api/manager/applications/{id}/decision` | Решение менеджера |

## Статусы

```
DRAFT -> ENRICHMENT -> SCORING -> DECISION -> APPROVED / COUNTER_OFFER / REJECTED / MANUAL_REVIEW
MANUAL_REVIEW -> APPROVED / COUNTER_OFFER / REJECTED / NEEDS_MORE_INFO
NEEDS_MORE_INFO -> MANUAL_REVIEW
APPROVED / COUNTER_OFFER -> SIGNING -> FUNDED
```

`SUBMITTED`, `CANCELLED` и `EXPIRED` оставлены в enum только для чтения старых строк БД.

## Скоринг и решение

- 10 факторов, балл `600 + 2 × сумма факторов`, шкала 0–1000.
- Грейды: A ≥ 700, B ≥ 600, C ≥ 500, D < 500.
- Лимиты без залога: A — 40% выручки, B — 30%, C — 20%.
- Ставка: базовая ставка продукта + надбавка грейда (A 0, B +0.5, C +1.0 п.п.).
- Стоп-факторы: ликвидация, приостановка счетов, просрочка 90+ дней.
- Недоступный источник или сумма > 10 млн — ручное рассмотрение.
- При превышении лимита формируются три варианта: лимит, поручительство под 19.5%,
  промежуточная сумма под залог.

Факторы хранятся в `scoringDetails` как JSON с кодами причин R01–R06; старый формат
`Название (баллы)` продолжает читаться.

## Демо-ИНН

| ИНН | Сценарий |
|---|---|
| `7704983159` | Грейд A, одобрение |
| `7703168426` | Грейд C, контрпредложение |
| `7729501760` | Грейд D, отказ |
| `7722085341` | Ошибка БКИ, ручное рассмотрение |
| `7731497609` | ФНС временно недоступна, затем успех |
| `7736150978` | Ликвидированная компания, стоп-фактор |

## Тесты

```bash
./mvnw test
```

Unit-тесты проверяют три основных ИНН, границы DTI, стоп-факторы, контрпредложения,
расчёт платежа/переплаты и переходы статусов. Интеграционный тест поднимает
PostgreSQL и Redis через Testcontainers.

## ИИ-объяснения

`AiAssistantClient` использует OpenAI-совместимый `/v1/chat/completions`
(подходит Ollama и внешние провайдеры). Настройки:

```yaml
app:
  ai:
    base-url: ${APP_AI_BASE_URL:http://localhost:11434}
    api-key: ${APP_AI_API_KEY:}
    model: ${APP_AI_MODEL:qwen2.5:7b}
    timeout-seconds: ${APP_AI_TIMEOUT_SECONDS:10}
```

Если ИИ недоступен или в ответе появляются числа, которых нет во входных данных,
возвращается детерминированный шаблон.
