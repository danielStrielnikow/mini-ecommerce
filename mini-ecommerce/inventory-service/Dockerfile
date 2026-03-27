# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY shared-events/pom.xml shared-events/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml

RUN ./mvnw dependency:go-offline -pl inventory-service -am -q

COPY shared-events/src shared-events/src
COPY inventory-service/src inventory-service/src
RUN ./mvnw package -pl inventory-service -am -DskipTests -q

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/inventory-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
