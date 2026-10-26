# Pinned multi-architecture base manifests; update through reviewed dependency maintenance.
FROM eclipse-temurin:21-jdk-alpine@sha256:fd52084c35bbac78890d95d57707d31ffc9d7a3324199e89c06be2a9dc71bfa0 AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src ./src
# CI runs the full suite (including PostgreSQL) before building the image.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088 AS runtime
WORKDIR /app
RUN apk add --no-cache --upgrade 'brotli-libs=1.2.1-r0'
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
