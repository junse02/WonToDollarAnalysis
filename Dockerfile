# syntax=docker/dockerfile:1

# ── 1단계: 빌드 ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 레이어 캐시: 빌드 스크립트/래퍼 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 boot jar 빌드 (테스트는 CI에서 수행하므로 여기선 제외)
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon -x test

# ── 2단계: 런타임 ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# 비루트 사용자
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /app/data && chown -R app:app /app
USER app

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
