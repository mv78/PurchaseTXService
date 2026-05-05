# Copyright (c) 2026 Mike Veksler. All rights reserved.

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Resolve dependencies before copying source so this layer is cached
# across source-only changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: extract Spring Boot layers ───────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS layers
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 3: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user — never run application processes as root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

# Copy layers in order of change frequency (least → most) to maximise
# cache reuse between releases.
COPY --from=layers --chown=spring:spring /app/dependencies/ ./
COPY --from=layers --chown=spring:spring /app/spring-boot-loader/ ./
COPY --from=layers --chown=spring:spring /app/snapshot-dependencies/ ./
COPY --from=layers --chown=spring:spring /app/application/ ./

# ── JVM tuning ────────────────────────────────────────────────────────────────
# JAVA_TOOL_OPTIONS is read by the JVM before main(); no wrapper script needed.
#
#   MaxRAMPercentage    – cap heap at 75 % of the container memory limit
#   InitialRAMPercentage– commit 50 % upfront to avoid GC storms on warm-up
#   ExitOnOutOfMemoryError – crash fast so the orchestrator can restart cleanly
#   UseZGC              – low-pause GC; well-suited for Java 21 + containers
#   security.egd        – avoid /dev/random blocking on entropy-scarce hosts
ENV JAVA_TOOL_OPTIONS="\
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+UseZGC \
  -Djava.security.egd=file:/dev/./urandom \
  -Duser.timezone=UTC"

# ── Spring configuration ──────────────────────────────────────────────────────
ENV SPRING_PROFILES_ACTIVE=prod

# DB_URL, DB_USERNAME, DB_PASSWORD must be injected at runtime via -e flags,
# Docker secrets, or a secrets manager. Do not set them here.

EXPOSE 8080

# Docker sends SIGTERM on stop; Spring Boot's graceful shutdown drains
# in-flight requests before the process exits.
STOPSIGNAL SIGTERM

# Liveness/readiness via Spring Actuator (/actuator/health).
# start-period gives the JVM and Flyway migrations time to finish.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
