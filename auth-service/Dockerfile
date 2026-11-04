# ---------- Étape 1 : compilation (l'image Maven contient déjà Maven) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Réglage réseau optionnel (vide par défaut). Utile si l'IPv6 d'une machine est instable :
#   docker compose build --build-arg JAVA_NET_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true"
ARG JAVA_NET_OPTS=""
ENV JAVA_TOOL_OPTIONS=${JAVA_NET_OPTS}

COPY pom.xml .
COPY src ./src

# Garde-manger (cache) : ce que Maven télécharge est conservé entre deux builds.
# 2 téléchargements à la fois, 60 s pour se connecter, 6 essais par fichier.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B --no-transfer-progress clean package -DskipTests \
      -Dmaven.artifact.threads=2 \
      -Daether.connector.http.connectionTimeout=60000 \
      -Daether.connector.http.retryHandler.count=6

# ---------- Étape 2 : exécution ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Le service ne tourne pas en administrateur
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/*.jar app.jar

USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
