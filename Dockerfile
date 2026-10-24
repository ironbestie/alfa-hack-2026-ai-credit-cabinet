FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -q -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app app

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl -fsS -u client:password http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
