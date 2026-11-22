# DwaDispo – Sécurité DevSecOps

## Objectif

Documenter les mesures de sécurité et d'authentification du projet DwaDispo.

## Politique d'accès

| Fonctionnalité | Accès |
| --- | --- |
| Consultation des pharmacies | Public |
| Pharmacies de garde | Public |
| Recherche de médicaments | Public, lecture seule |
| Scan d'ordonnance | Patient authentifié |
| Historique des ordonnances | Patient authentifié |
| Création d'une demande | Patient authentifié |
| Suivi des demandes | Patient authentifié, propriétaire uniquement |
| Gestion des pharmacies et des gardes | Pharmacien authentifié |

Les accès doivent être contrôlés côté backend.
Le masquage des liens dans Angular ne constitue pas une protection suffisante.

## Mesures de sécurité

- Authentification avec JWT.
- Contrôle des rôles Patient et Pharmacien.
- Protection des routes Angular avec des guards.
- Vérification des autorisations côté backend.
- Validation des données reçues par les API.
- Protection contre les abus et les requêtes excessives.
- Protection des données personnelles et des ordonnances.
- HTTPS/TLS prévu avant le déploiement.

## Lancer le projet

Depuis la racine du dépôt :

```bash
docker compose up --build -d
```

Vérifier les conteneurs :

```bash
docker compose ps
```

Consulter les logs de l'API Gateway :

```bash
docker compose logs -f api-gateway
```

## Récupérer les changements de l'équipe

Après fusion de la Merge Request dans develop :

```bash
git switch develop
git pull origin develop
```

## HTTPS / TLS

HTTPS sera configuré dans une étape dédiée, devant l'API Gateway, avant le déploiement.
Le développement local peut continuer sur localhost en HTTP.
