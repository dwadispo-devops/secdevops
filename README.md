## Créer un nouveau microservice

1. Copiez le dossier `_template/` et renommez la copie avec le nom du service
   (ex: `medication-service`, `booking-service`).
2. Dans `pom.xml`, changez `<artifactId>service-template</artifactId>` par le nom du nouveau service.
3. Dans `application.properties`, remplacez `nom-du-service` et `nom_db` par les vrais noms
   (ex: `medication-service`, `medication_db`), et changez le port (8082, 8083...).
4. Renommez le dossier `com/dwadispo/servicetemplate` en `com/dwadispo/votreservice`.
5. Ouvrez la classe principale et mettez à jour son nom de package et de classe.

## Démarrage rapide avec Docker

Prérequis : Docker avec le plugin Compose.

```bash
cp .env.example .env
# Ouvrir .env et remplacer les valeurs :
#   DB_PASSWORD : un mot de passe de votre choix
#   JWT_SECRET  : générer avec  openssl rand -base64 48
docker compose up -d --build
docker compose ps
```

- `auth-service` : http://localhost:8081 (base `auth-db` sur le port 5432)
- `eureka-server` : http://localhost:8761 (annuaire des microservices)
- `api-gateway` : http://localhost:8080 (point d'entrée qui route vers les services)

Vérifiez que tout fonctionne en ouvrant http://localhost:8761 :
API-GATEWAY doit apparaître dans la liste, avec le statut UP.

- Arrêter : `docker compose down` (les données sont conservées dans un volume)
- Tout effacer, y compris les données : `docker compose down -v`
- Le fichier `.env` n'est jamais versionné ; seul `.env.example` l'est.
- Le premier démarrage d'auth-service prend 30 à 40 secondes : suivre avec `docker compose logs -f auth-service` et attendre la ligne `Started AuthServiceApplication` avant de tester.
