# MédiDispo / SecDevOps-Projet

Plateforme de réservation de médicaments en pharmacie, avec scan d'ordonnance par IA.
Architecture microservices (Spring Boot), méthode Scrum, CI/CD GitLab.

## Créer un nouveau microservice

1. Copiez le dossier `_template/` et renommez la copie avec le nom du service 
   (ex: `medication-service`, `booking-service`).
2. Dans `pom.xml`, changez `<artifactId>service-template</artifactId>` par le nom du nouveau service.
3. Dans `application.properties`, remplacez `nom-du-service` et `nom_db` par les vrais noms 
   (ex: `medication-service`, `medication_db`), et changez le port (8082, 8083...).
4. Renommez le dossier `com/dwadispo/servicetemplate` en `com/dwadispo/votreservice`.
5. Ouvrez la classe principale et mettez à jour son nom de package et de classe.

## Lancer le socle technique (Eureka + Gateway)

```powershell
docker compose up --build
```

Cela démarre deux services :
- **eureka-server** sur http://localhost:8761 (annuaire des microservices)
- **api-gateway** sur http://localhost:8080 (point d'entrée qui route vers les services)

Vérifiez que tout fonctionne en ouvrant http://localhost:8761 : 
API-GATEWAY doit apparaître dans la liste, avec le statut UP.

Pour arrêter :
```powershell
docker compose down
```

## Structure du projet

```
secdevops/
  ├── eureka-server/      (annuaire des microservices)
  ├── api-gateway/        (passerelle API, point d'entrée)
  ├── _template/           (modèle pour créer un nouveau microservice)
  ├── auth-service/        (comptes, connexion)
  ├── pharmacy-service/    (pharmacies, recherche)
  ├── docker-compose.yml
  └── README.md
```