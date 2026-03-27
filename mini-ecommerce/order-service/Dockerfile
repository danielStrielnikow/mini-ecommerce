# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY shared-events/pom.xml shared-events/pom.xml
COPY order-service/pom.xml order-service/pom.xml

RUN ./mvnw dependency:go-offline -pl order-service -am -q

COPY shared-events/src shared-events/src
COPY order-service/src order-service/src
RUN ./mvnw package -pl order-service -am -DskipTests -q

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/order-service/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
