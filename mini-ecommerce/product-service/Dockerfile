# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and parent pom first (better layer caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY shared-events/pom.xml shared-events/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY e2e-tests/pom.xml e2e-tests/pom.xml

# Download dependencies (cached layer unless pom changes)
RUN ./mvnw dependency:go-offline -pl product-service -am -q

# Copy source and build
COPY shared-events/src shared-events/src
COPY product-service/src product-service/src
RUN ./mvnw package -pl product-service -am -DskipTests -q

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/product-service/target/*-exec.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
