# ============================================================
# StockMate — Multi-stage Dockerfile
# Stage 1 : Build the fat JAR with Maven
# Stage 2 : Run on a slim JRE alpine image
# ============================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom first (layer cache — only re-downloads
# dependencies when pom.xml changes, not on every source change)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build — tests are skipped here; CI runs them separately
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user for security best practice
RUN addgroup -S stockmate && adduser -S stockmate -G stockmate
USER stockmate

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]