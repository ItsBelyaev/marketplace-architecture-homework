FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /app/target/marketplace.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
