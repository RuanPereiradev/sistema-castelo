# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY shared-kernel/pom.xml shared-kernel/pom.xml
COPY identity/pom.xml identity/pom.xml
COPY hotel/pom.xml hotel/pom.xml
COPY restaurant/pom.xml restaurant/pom.xml
COPY billing/pom.xml billing/pom.xml
COPY tax-invoice/pom.xml tax-invoice/pom.xml
COPY payment/pom.xml payment/pom.xml
COPY app/pom.xml app/pom.xml

RUN mvn -B -q dependency:go-offline || true

COPY shared-kernel shared-kernel
COPY identity identity
COPY hotel hotel
COPY restaurant restaurant
COPY billing billing
COPY tax-invoice tax-invoice
COPY payment payment
COPY app app

RUN mvn -B -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S castel && adduser -S castel -G castel
USER castel

COPY --from=build /workspace/app/target/app-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
