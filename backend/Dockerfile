# ---------- Étape 1 : build (JDK + Maven), jetée après la compilation ----------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Dépendances d'abord : cette couche reste en cache tant que pom.xml ne change pas
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Les tests d'intégration (Testcontainers) tournent hors de Docker : mvnw verify
RUN mvn -B -ntp -DskipTests package

# ---------- Étape 2 : exécution (JRE seul, sans Maven ni sources) ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Ne pas tourner en root dans le conteneur
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/backend-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
