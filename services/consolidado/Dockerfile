# ── Stage 1: Builder ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY services/consolidado/gradle/           services/consolidado/gradle/
COPY services/consolidado/gradlew           services/consolidado/gradlew
COPY services/consolidado/build.gradle      services/consolidado/build.gradle
COPY services/consolidado/settings.gradle   services/consolidado/settings.gradle
COPY services/consolidado/gradle.properties services/consolidado/gradle.properties

COPY contracts/openapi/consolidado.yaml     contracts/openapi/consolidado.yaml

WORKDIR /workspace/services/consolidado

RUN ./gradlew dependencies --no-daemon -q 2>&1 | tail -3

COPY services/consolidado/src src/
RUN ./gradlew bootJar --no-daemon -q

RUN java -Djarmode=tools \
    -jar build/libs/consolidado-*.jar extract \
    --layers --launcher \
    --destination build/extracted

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12:nonroot AS runtime

WORKDIR /app

COPY --from=builder --chown=65532:65532 \
    /workspace/services/consolidado/build/extracted/dependencies/ ./
COPY --from=builder --chown=65532:65532 \
    /workspace/services/consolidado/build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=65532:65532 \
    /workspace/services/consolidado/build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=65532:65532 \
    /workspace/services/consolidado/build/extracted/application/ ./

EXPOSE 8080

HEALTHCHECK NONE

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
