# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS builder

ARG GRADLE_USER_HOME=/root/.gradle
ENV GRADLE_USER_HOME=${GRADLE_USER_HOME}

WORKDIR /app

COPY orderDomain-service/gradlew .
COPY orderDomain-service/gradle ./gradle

RUN --mount=type=cache,target=${GRADLE_USER_HOME} \
    --mount=type=cache,target=${GRADLE_USER_HOME}/wrapper \
    chmod +x ./gradlew && \
    ./gradlew --version --no-daemon

COPY orderDomain-service/settings.gradle ./
COPY orderDomain-service/build.gradle ./

COPY contracts ./contracts

RUN --mount=type=cache,target=${GRADLE_USER_HOME} \
    --mount=type=cache,target=${GRADLE_USER_HOME}/wrapper \
    ./gradlew dependencies --no-daemon --console=plain

COPY orderDomain-service/src ./src

RUN --mount=type=cache,target=${GRADLE_USER_HOME} \
    --mount=type=cache,target=${GRADLE_USER_HOME}/wrapper \
    ./gradlew bootJar --no-daemon \
        -x test \
        -x check \
        -x jacocoTestVerification \
        -x spotlessApply \
        --console=plain

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar /app/orderDomain-service.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/orderDomain-service.jar"]
