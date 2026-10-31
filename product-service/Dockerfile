# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS builder

ARG GRADLE_USER_HOME=/root/.gradle
ENV GRADLE_USER_HOME=${GRADLE_USER_HOME}

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle

RUN --mount=type=cache,target=${GRADLE_USER_HOME} \
    --mount=type=cache,target=${GRADLE_USER_HOME}/wrapper \
    chmod +x ./gradlew && \
    ./gradlew --version --no-daemon

COPY settings.gradle ./
COPY build.gradle ./

RUN --mount=type=cache,target=${GRADLE_USER_HOME} \
    --mount=type=cache,target=${GRADLE_USER_HOME}/wrapper \
    ./gradlew dependencies --no-daemon --console=plain

COPY config ./config
COPY src ./src

RUN --mount=type=cache,target=${GRADLE_USER_HOME} \
    --mount=type=cache,target=${GRADLE_USER_HOME}/wrapper \
    ./gradlew bootJar --no-daemon \
        -x test \
        -x check \
        -x spotlessCheck \
        -x spotlessApply \
        --console=plain

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar /app/product-service.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/product-service.jar"]
