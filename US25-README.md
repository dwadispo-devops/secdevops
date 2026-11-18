# US25 – Dashboard Pharmacien

## Objectif

En tant que pharmacien, je souhaite disposer d'un tableau de bord
pour suivre les demandes de disponibilité, mon taux de réponse
et les réservations.

## Fonctionnalités

- Consultation des indicateurs du tableau de bord.
- Suivi des demandes reçues.
- Suivi du taux de réponse.
- Suivi des réservations.
- Accès aux fonctionnalités de gestion du stock et des pharmacies de garde.

## Architecture

- Frontend : Angular.
- Backend : Spring Boot – pharmacy-service.
- Communication : API REST via api-gateway.
- Base de données : PostgreSQL (via Docker Compose).

## Lancement du projet

Depuis la racine du dépôt :

\`\`\`bash
docker compose up -d --build
docker compose ps
\`\`\`

## Tests backend

\`\`\`bash
cd pharmacy-service
mvn test
cd ..
\`\`\`

## Consultation des logs

\`\`\`bash
docker compose logs -f pharmacy-service
docker compose logs -f api-gateway
\`\`\`

## Accès à l'application

- Frontend : http://localhost:4200
- API Gateway : http://localhost:8080
- Eureka : http://localhost:8761

## Branche de développement

\`\`\`bash
git checkout DEVOPS-37-us25-dashboard-pharmacien
\`\`\`

## Intégration dans develop

Après validation et commit de US25 :

\`\`\`bash
git checkout develop
git pull origin develop
git merge DEVOPS-37-us25-dashboard-pharmacien
git push origin develop
\`\`\`

