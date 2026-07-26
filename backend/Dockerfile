# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Abhängigkeiten zuerst cachen (nur neu laden wenn pom.xml sich ändert)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user für Sicherheit
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

COPY --from=build --chown=appuser:appgroup /app/target/*.war /app/app.war

USER appuser

EXPOSE 8080
# Actuator-Port
EXPOSE 9090

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/localhost/9090 && echo -e "GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && grep -q "UP" <&3'

ENTRYPOINT ["java", "-jar", "/app/app.war"]
