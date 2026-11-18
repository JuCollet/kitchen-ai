# syntax=docker/dockerfile:1
#
# Kitchen API image. The build context is the repository root, because the MCP widget lives
# outside apps/api:
#   docker build -f apps/api/Dockerfile -t kitchen-api .

# 1. MCP widget: a single self-contained HTML file that Maven copies onto the API classpath
#    (see the resources section of apps/api/pom.xml). It must exist before the jar is built.
FROM node:26.13.1-slim AS widget
WORKDIR /repo
RUN npm install --global pnpm@12.21.0
# Manifests first: the install layer is reused as long as the dependencies do not change.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/
COPY packages/shopping-list/package.json packages/shopping-list/
RUN pnpm install --frozen-lockfile --filter @kitchen/shopping-list...
COPY packages/shopping-list packages/shopping-list
RUN pnpm --filter @kitchen/shopping-list build

# 2. API jar. Tests are not run here: CI runs them before building the image, and the
#    Testcontainers tests need a Docker daemon, which this build does not have.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /repo/apps/api
COPY apps/api/.mvn .mvn
COPY apps/api/mvnw apps/api/pom.xml ./
# Dependencies first, for the same layer-reuse reason as above.
RUN ./mvnw -B dependency:go-offline
COPY apps/api/src src
COPY --from=widget /repo/packages/shopping-list/dist /repo/packages/shopping-list/dist
RUN ./mvnw -B -DskipTests package

# 3. Runtime: JRE only (no compiler, no Maven), running as a non-root user. The numeric UID lets
#    Kubernetes check "runAsNonRoot" without looking the user up.
FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /repo/apps/api/target/*.jar app.jar
USER 10001:10001
EXPOSE 8080
# JVM options (MaxRAMPercentage...) are set by the Kubernetes manifest through JAVA_TOOL_OPTIONS,
# so they can be tuned without rebuilding the image.
ENTRYPOINT ["java", "-jar", "app.jar"]
