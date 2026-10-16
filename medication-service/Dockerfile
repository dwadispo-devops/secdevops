# ---------- Étape 1 : Compilation ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Optimisation du cache Docker : téléchargement des dépendances
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Copie du code source et compilation du livrable
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Étape 2 : Exécution ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Sécurité SecDevOps : exécution sous un utilisateur non-privilégié
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/*.jar app.jar

USER app
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
