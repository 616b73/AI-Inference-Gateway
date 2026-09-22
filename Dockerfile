# Pinned multi-architecture base manifests; update through reviewed dependency maintenance.
FROM eclipse-temurin:21-jdk-alpine@sha256:cd87715a8d45cfaa42419207c64680234f62785c49055cccd20437b5c9018380 AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src ./src
# CI runs the full suite (including PostgreSQL) before building the image.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:1a29e1fe337eb28b5bec30f0ee8ed29f0ff80ab6f75dcf9313efe82911065a52 AS runtime
WORKDIR /app
RUN addgroup -g 10001 gateway && adduser -D -H -u 10001 -G gateway gateway
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "app.jar"]

# CI packages the exact tested/manifested artifact, avoiding a second compilation.
FROM runtime AS packaged
COPY --chown=10001:10001 target/ai-inference-gateway-0.0.1-SNAPSHOT.jar app.jar

# Default local Compose build remains self-contained from source.
FROM runtime AS final
COPY --from=build --chown=10001:10001 /app/target/ai-inference-gateway-0.0.1-SNAPSHOT.jar app.jar
