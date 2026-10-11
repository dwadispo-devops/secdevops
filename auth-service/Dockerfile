# ---------- Étape 1 : la cuisine (compilation) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# On copie d'abord ce qui change rarement (pom + wrapper Maven)
# pour que Docker réutilise son cache et gagne du temps
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Puis le code source, qui change souvent
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# ---------- Étape 2 : la boîte repas (exécution) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Le service ne tourne pas en administrateur
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/*.jar app.jar

USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
