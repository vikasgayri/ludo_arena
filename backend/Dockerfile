# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies first, so they are cached between builds when only the source changes
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
# Tests run in CI / locally (mvn clean test); the image build only packages the jar
RUN mvn -q -B clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# never run the app as root
RUN useradd --system --uid 10001 --no-create-home ludo
COPY --from=build /app/target/*.jar app.jar
USER ludo

# Render (and most hosts) inject PORT; Spring reads it via server.port=${PORT:8080}
EXPOSE 8080
# Fit small containers (e.g. Render free/starter 512 MB): heap = 75% of the container limit, lightweight GC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
