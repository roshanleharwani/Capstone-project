# Stage 1: Build the Maven project
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy the parent pom and all module poms
COPY pom.xml .
COPY modules/ingress-shield/pom.xml modules/ingress-shield/
COPY modules/context-compression/pom.xml modules/context-compression/
COPY modules/market-incentives/pom.xml modules/market-incentives/
COPY modules/router-dashboard/pom.xml modules/router-dashboard/
COPY nexus-gateway-app/pom.xml nexus-gateway-app/
COPY contracts/ contracts/ 

# Copy source code
COPY modules/ modules/
COPY nexus-gateway-app/src nexus-gateway-app/src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/nexus-gateway-app/target/nexus-gateway-app-*.jar app.jar

# Expose the application port (typically 8080 for Spring Boot)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
