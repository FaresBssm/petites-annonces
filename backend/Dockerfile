# syntax=docker/dockerfile:1
# Image légère en 3 étapes : build Maven -> JRE réduit par jlink -> exécution sur Alpine nu.

# ---------- Étape 1 : build (JDK + Maven), jetée après la compilation ----------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Dépendances d'abord : cette couche reste en cache tant que pom.xml ne change pas
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Les tests d'intégration (Testcontainers) tournent hors de l'image : mvnw verify / pipeline CI
RUN mvn -B -ntp -DskipTests package \
 && cp target/backend-*.jar app.jar \
 # Jar découpé en couches (dépendances / chargeur / code) : une mise à jour du code
 # ne retélécharge que la petite couche "application"
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------- Étape 2 : JRE réduit aux seuls modules Java utilisés ----------
FROM eclipse-temurin:21-jdk-alpine AS jre
WORKDIR /jre-build
COPY --from=build /build/extracted ./extracted
# jdeps calcule les modules réellement utilisés par l'application et ses dépendances ;
# on ajoute ceux chargés dynamiquement (réflexion, JMX, crypto) que l'analyse statique ne voit pas.
RUN MODULES=$(jdeps --ignore-missing-deps -q --recursive --multi-release 21 --print-module-deps \
        --class-path "extracted/dependencies/BOOT-INF/lib/*" \
        extracted/application/BOOT-INF/classes) \
 && echo "Modules détectés : ${MODULES}" \
 && jlink --add-modules "${MODULES},jdk.crypto.ec,jdk.unsupported,java.instrument,jdk.management,java.security.jgss,jdk.naming.dns" \
        --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
        --output /opt/jre

# ---------- Étape 3 : exécution (Alpine + JRE réduit + couches de l'application) ----------
FROM alpine:3.22
LABEL org.opencontainers.image.title="petites-annonces-backend" \
      org.opencontainers.image.description="API PetitesAnnonces (Spring Boot 3, sécurité par headers de la gateway)"

ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:$PATH

COPY --from=jre /opt/jre /opt/jre

# Ne pas tourner en root dans le conteneur
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

# Du moins souvent modifié au plus souvent modifié : meilleure réutilisation du cache et des couches
COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

EXPOSE 8081
# Jar extrait en couches : pas de app.jar, on lance le chargeur de Spring Boot depuis /app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
