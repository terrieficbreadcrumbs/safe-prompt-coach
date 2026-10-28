# AI Without the Fear: Safe Prompt Coach
# Two-stage build: Maven builds target/safe-prompt-coach.jar, then a small Java runtime image runs it.

# ---- Build stage: full JDK + Maven ----
FROM maven:3.10.0-eclipse-temurin-25 AS build
WORKDIR /build

# Download dependencies first, so this layer is reused when only the source code changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run locally with "mvn test" before pushing. They're skipped here to keep deploy builds fast.
RUN mvn -B -DskipTests package

# ---- Runtime stage: Java runtime only ----
FROM eclipse-temurin:25.0.4.1_1-jre

# Run as a regular user, not root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin coach
WORKDIR /app
COPY --from=build /build/target/safe-prompt-coach.jar ./safe-prompt-coach.jar
USER coach

# The app listens on the PORT environment variable (Render sets it), or 8080 if PORT isn't set.
EXPOSE 8080

# Let Java use most of the container's memory, which matters on small free-tier instances.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/safe-prompt-coach.jar"]
