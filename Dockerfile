# ---- build ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package

# ---- run ----
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]