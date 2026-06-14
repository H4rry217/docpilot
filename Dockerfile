FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend

# Build the DocPilot frontend assets that will be packaged into the Spring Boot jar.
COPY docpilot-frontend/package*.json ./
RUN npm ci
COPY docpilot-frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-25 AS backend-build
WORKDIR /workspace

# Copy Maven descriptors first so dependency resolution can be cached between source changes.
COPY pom.xml ./
COPY docpilot-shared/pom.xml docpilot-shared/pom.xml
COPY docpilot-shared/docpilot-common/pom.xml docpilot-shared/docpilot-common/pom.xml
COPY docpilot-shared/docpilot-web-common/pom.xml docpilot-shared/docpilot-web-common/pom.xml
COPY docpilot-block/pom.xml docpilot-block/pom.xml
COPY docpilot-filesystem/pom.xml docpilot-filesystem/pom.xml
COPY docpilot-ai/pom.xml docpilot-ai/pom.xml
COPY docpilot-services/pom.xml docpilot-services/pom.xml
COPY docpilot-services/docpilot-user-service/pom.xml docpilot-services/docpilot-user-service/pom.xml
COPY docpilot-services/docpilot-workspace-service/pom.xml docpilot-services/docpilot-workspace-service/pom.xml
COPY docpilot-services/docpilot-web-service/pom.xml docpilot-services/docpilot-web-service/pom.xml
RUN mvn -B -pl docpilot-services/docpilot-web-service -am dependency:go-offline

COPY docpilot-shared/ docpilot-shared/
COPY docpilot-block/ docpilot-block/
COPY docpilot-filesystem/ docpilot-filesystem/
COPY docpilot-ai/ docpilot-ai/
COPY docpilot-services/ docpilot-services/
COPY --from=frontend-build /workspace/frontend/dist/ docpilot-services/docpilot-web-service/src/main/resources/static/

RUN mvn -B -pl docpilot-services/docpilot-web-service -am package -DskipTests \
    && cp docpilot-services/docpilot-web-service/target/docpilot-web-service-*.jar /workspace/docpilot.jar

FROM eclipse-temurin:25-jre
USER root
WORKDIR /opt/docpilot

ENV JAVA_OPTS=""
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=backend-build /workspace/docpilot.jar /opt/docpilot/docpilot.jar

EXPOSE 11451
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /opt/docpilot/docpilot.jar"]
