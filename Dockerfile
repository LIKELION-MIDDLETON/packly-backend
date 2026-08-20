FROM gradle:9.5.1-jdk17 AS build

WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system centralton \
    && useradd --system --gid centralton --no-create-home centralton

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/centralton-api.jar
RUN chown -R centralton:centralton /app
USER centralton

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
    CMD curl --fail --silent http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/centralton-api.jar"]
