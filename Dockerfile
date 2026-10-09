# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring
COPY --from=build --chown=spring:spring /workspace/target/platform-0.0.1-SNAPSHOT.jar /app/platform.jar

USER spring:spring
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/platform.jar"]
