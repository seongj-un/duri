# ---------------------------------------------------------------------
# 1) 빌드 — 의존성 레이어를 소스보다 먼저 굳혀 재빌드를 빠르게 한다
# ---------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src src
# 테스트는 CI 에서 Docker(Testcontainers)와 함께 돌린다. 이미지 빌드에서는 건너뛴다.
RUN ./gradlew --no-daemon bootJar -x test

# ---------------------------------------------------------------------
# 2) 실행 — JRE 만 담아 이미지를 가볍게
# ---------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# HEALTHCHECK 용 curl. root 로 돌리지 않도록 전용 사용자도 만든다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system duri && useradd --system --gid duri duri
COPY --from=builder --chown=duri:duri /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER duri

EXPOSE 8080
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

# 컨테이너 오케스트레이터가 준비 상태를 알 수 있도록
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
