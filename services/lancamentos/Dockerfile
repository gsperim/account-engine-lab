# ── Stage 1: Builder ──────────────────────────────────────────────────────────
# eclipse-temurin:21-jdk-jammy — JDK completo só no builder (descartado ao final)
# Contexto de build: raiz do repositório (necessário para contracts/openapi/)
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# Gradle wrapper e arquivos de build (camada de cache separada do código-fonte)
COPY services/lancamentos/gradle/           services/lancamentos/gradle/
COPY services/lancamentos/gradlew           services/lancamentos/gradlew
COPY services/lancamentos/build.gradle      services/lancamentos/build.gradle
COPY services/lancamentos/settings.gradle   services/lancamentos/settings.gradle
COPY services/lancamentos/gradle.properties services/lancamentos/gradle.properties

# Contratos OpenAPI (openApiGenerate resolve ../../contracts a partir de rootDir)
COPY contracts/openapi/lancamentos.yaml     contracts/openapi/lancamentos.yaml

WORKDIR /workspace/services/lancamentos

# Corrige gradle.properties: substitui o path do host pelo JAVA_HOME do container
RUN sed -i "s|org.gradle.java.home=.*|org.gradle.java.home=${JAVA_HOME}|" gradle.properties

# Download de dependências em camada isolada (cache Docker entre builds)
RUN ./gradlew dependencies --no-daemon -q 2>&1 | tail -3

# Código-fonte e empacotamento
COPY services/lancamentos/src src/
RUN ./gradlew bootJar --no-daemon -q

# Extrai layers do fat JAR para cache granular no stage de runtime
RUN java -Djarmode=tools \
    -jar build/libs/lancamentos-*.jar extract \
    --layers --launcher \
    --destination build/extracted

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# distroless: sem shell, sem package manager, surface de ataque mínima.
# :nonroot roda com uid 65532 por padrão — zero configuração extra necessária.
FROM gcr.io/distroless/java21-debian12:nonroot AS runtime

WORKDIR /app

# Layers em ordem crescente de frequência de mudança:
# dependências (raramente) → loader (raramente) → snapshots → aplicação (sempre)
COPY --from=builder --chown=65532:65532 \
    /workspace/services/lancamentos/build/extracted/dependencies/ ./
COPY --from=builder --chown=65532:65532 \
    /workspace/services/lancamentos/build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=65532:65532 \
    /workspace/services/lancamentos/build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=65532:65532 \
    /workspace/services/lancamentos/build/extracted/application/ ./

EXPOSE 8080

# Sem shell/curl no distroless — health gerido por HTTP probe no Kubernetes (/actuator/health)
HEALTHCHECK NONE

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]

# ── Stage 3: Outbox Relay (Etapa 8) ───────────────────────────────────────────
FROM runtime AS outbox-relay
ENV SPRING_PROFILES_ACTIVE=outbox-relay
