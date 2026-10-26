# Версии и воспроизводимость сборки

Проект собирается детерминированно: версии языка, Maven, базовых Docker-образов
и библиотек зафиксированы. Ниже — где именно это закреплено и как обновлять.

## Backend

| Что | Версия | Где закреплено |
|---|---|---|
| Java (target bytecode) | 21 | `pom.xml` → `java.version`, `kotlin-maven-plugin` → `jvmTarget` |
| Maven (локально) | 3.9.6+ | `.mvn/wrapper/maven-wrapper.properties` (wrapper), enforcer требует `>= 3.9.6` |
| Maven (Docker build) | из образа `maven:3.9-eclipse-temurin-21` | `Dockerfile`, образ закреплён по digest |
| JRE (runtime) | 21 | `Dockerfile`, `eclipse-temurin:21-jre` по digest |
| Spring Boot | 3.5.0 | `pom.xml` → parent |
| Kotlin | 2.1.0 | `pom.xml` → `kotlin.version` |
| Spring Modulith | 1.3.0 | `pom.xml` → `spring-modulith.version` + BOM |
| Flyway | 11.7.2 | версия из Spring Boot BOM |
| PostgreSQL JDBC | 42.7.5 | версия из Spring Boot BOM |
| AWS SDK S3 | 2.25.60 | `pom.xml` |
| Bucket4j | 8.10.1 | `pom.xml` |
| springdoc-openapi | 2.8.5 | `pom.xml` |
| Resilience4j | 2.2.0 | `pom.xml` |
| Maven Enforcer | 3.5.0 | `pom.xml` |

Spring Boot BOM фиксирует версии большинства транзитивных библиотек (Spring Framework,
Jackson, Hibernate, Flyway, Tomcat, Micrometer и т. д.). Поэтому обновление Spring Boot —
это одновременно и обновление согласованного набора зависимостей.

### Контроль версий

В `pom.xml` подключён `maven-enforcer-plugin`. Сборка падает, если:

- Maven старше 3.9.6;
- JDK моложе 21 (или новее 25);
- в `pom.xml` есть дубли зависимостей;
- нарушена сходимость версий (`dependencyConvergence`).

Проверить локально:

```bash
./mvnw -q validate
```

На Windows:

```powershell
.\mvnw.cmd -q validate
```

### Файл `dependency-lock.txt`

`dependency-lock.txt` — снимок фактических runtime-зависимостей из собранного
`app.jar` (`BOOT-INF/lib`). Он нужен, чтобы видеть точный набор версий, который
попал в образ, и замечать его изменение в diff.

Пересобрать снимок после изменения `pom.xml`:

```powershell
docker compose build app
$id = docker create credit-app:latest
docker cp "${id}:/app/app.jar" app-tmp.jar
docker rm $id
jar tf app-tmp.jar |
  Select-String "^BOOT-INF/lib/" |
  ForEach-Object { $_.Line -replace "^BOOT-INF/lib/", "" } |
  Sort-Object
```

Последние строки — это и есть актуальный список; добавьте к нему заголовок из
текущего `dependency-lock.txt`.

### Обновление backend-зависимостей

1. Измените версию в `pom.xml` (или версию Spring Boot parent).
2. Выполните `.\mvnw.cmd clean verify`.
3. Пересоберите образ: `docker compose build app`.
4. Обновите `dependency-lock.txt` по инструкции выше.
5. Прогоните smoke-проверку: `.\scripts\smoke.ps1`.

## Frontend

| Что | Версия | Где закреплено |
|---|---|---|
| Node.js | 24 | `web/Dockerfile`, образ `node:24-alpine` по digest |
| npm-зависимости | точные версии | `web/package-lock.json` |
| Next.js | 16.4.2 | `web/package.json` + lock |
| React / React DOM | 19.3.0 | `web/package.json` + lock |
| TypeScript | 5.x | `web/package.json` + lock |

Установка воспроизводится только через lock-файл:

```bash
cd web
npm ci
```

`npm install` обновляет lock-файл и для чистой сборки не используется.
В Docker выполняется тот же `npm ci`.

### Обновление frontend-зависимостей

```bash
cd web
npm install <package>@<version>
npm run typecheck
npm run lint
npm run build
```

После обновления обязательно коммитьте `package-lock.json`.

## Docker-образы

Все базовые образы закреплены по digest (`@sha256:...`), а не по плавающему тегу:

- `maven:3.9-eclipse-temurin-21@sha256:...` — сборка backend;
- `eclipse-temurin:21-jre@sha256:...` — runtime backend;
- `node:24-alpine@sha256:...` — сборка и runtime frontend;
- `postgres:16-alpine`, `redis:7-alpine`, `wiremock/wiremock:3.9.1` — инфраструктура;
- MinIO — `elestio/minio@sha256:...` (образ amd64; на ARM нужен другой digest).

Обновление digest — осознанное действие: сначала проверьте сборку и smoke-тесты,
затем меняйте строку `FROM`.

## Проверка перед сдачей

```powershell
# 1. Backend: компиляция, тесты, enforcer
.\mvnw.cmd clean verify

# 2. Frontend: типы, линтер, production-сборка
cd web
npm ci
npm run typecheck
npm run lint
npm run build
cd ..

# 3. Полный стек
docker compose up -d --build

# 4. Сквозная проверка API и веба
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
```
