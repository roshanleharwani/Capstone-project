# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy root pom and all module poms for dependency caching
COPY pom.xml .
COPY modules/ingress-shield/pom.xml modules/ingress-shield/
COPY modules/context-compression/pom.xml modules/context-compression/
COPY modules/market-incentives/pom.xml modules/market-incentives/
COPY modules/router-dashboard/pom.xml modules/router-dashboard/
COPY nexus-gateway-app/pom.xml nexus-gateway-app/
COPY contracts/ contracts/

# Resolve dependencies for router-dashboard only (cache layer)
RUN mvn dependency:go-offline -pl modules/router-dashboard -am -q

# Copy router-dashboard source and build
COPY modules/router-dashboard/src modules/router-dashboard/src
RUN mvn clean package -pl modules/router-dashboard -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=builder /app/modules/router-dashboard/target/router-dashboard-*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
