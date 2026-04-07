# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests dependency:go-offline

COPY src ./src
COPY sql ./sql

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -T 1C -DskipTests package

FROM alpine:3.21 AS skywalking-agent

ARG SKYWALKING_AGENT_VERSION=9.6.0

RUN apk add --no-cache curl tar
RUN mkdir -p /skywalking && \
    curl -fsSL "https://dlcdn.apache.org/skywalking/java-agent/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz" \
    | tar -xz -C /skywalking

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/target/*.jar /app/app.jar
COPY --from=skywalking-agent /skywalking/skywalking-agent /skywalking/agent

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
