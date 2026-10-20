# Build
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress
COPY src/ src/
RUN mvn package -DskipTests -B --no-transfer-progress

# Runtime
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /app/target/payflow-*.jar app.jar
EXPOSE 8080
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]