# Titre proposé

feat(us09): demander la disponibilité de médicaments auprès des pharmacies proches

# Description proposée

Le patient peut envoyer une demande contenant plusieurs médicaments et quantités depuis `/demande-disponibilite`, avec sa position GPS et un rayon de 100 à 20 000 mètres. Le backend valide le JWT PATIENT, persiste la demande et ses médicaments en statut initial PENDING, puis l'associe aux pharmacies APPROVED dans le rayon. Les pharmacies PENDING et hors rayon sont exclues.

La validation rejette les coordonnées absentes/non finies, les listes et noms invalides, les quantités non positives ou décimales, les rayons invalides et les JWT non autorisés. Une erreur pendant la création annule les écritures de la transaction. Angular transmet le Bearer via son proxy et affiche les confirmations et erreurs.

Tickets concernés : **DEVOPS-79, DEVOPS-80, DEVOPS-81, DEVOPS-82, DEVOPS-83, DEVOPS-84, DEVOPS-85, DEVOPS-86, DEVOPS-87** — US09. Les descriptions officielles de chaque ticket n'ont pas été accessibles ; cette référence ne prétend pas remplacer leurs critères officiels. Le commit existant `6bad6be` référence explicitement DEVOPS-79 à DEVOPS-81.

## Validation locale exécutée

- Maven Java 21 / PostgreSQL 16 + PostGIS Testcontainers : **61 tests réussis, 0 échec, 0 erreur, 0 ignoré**.
- Angular : **27 tests réussis, 0 échec, 0 ignoré** ; build de production réussi.
- Chrome headless : vrai register/login auprès d'auth-service, JWT émis par ce service et validé par pharmacy, saisie de deux médicaments, GPS contrôlé, rayon, soumission Angular via proxy, HTTP 201 et confirmation affichée.
- SQL : patient réel de test, médicaments et quantités exactes, demande PENDING, seule pharmacie APPROVED proche associée en PENDING.
- Cas négatifs depuis le navigateur : HTTP 400 avec rayon invalide, HTTP 401 avec JWT invalide et message Angular, HTTP 500 contrôlé dans la seule base E2E avec message Angular et annulation des écritures.
- `git diff --check` réussi ; V1/V2/V3 Flyway validées et exécutées dans la nouvelle base isolée ; bases existantes préservées.

Rapports : `docs/RAPPORT_DEVOPS_85_CODEX.md`, `docs/RAPPORT_DEVOPS_86_CODEX.md`, `docs/RAPPORT_DEVOPS_87_CODEX.md`.

## Périmètre et limites

- Migration V3 additive ; V1/V2/V3 déjà commitées sont inchangées par la validation.
- Les scripts `scripts/us09-e2e/` utilisent des fixtures fictives et une panne contrôlée strictement réservée à `pharmacy_e2e`. Ces fixtures ne sont pas des migrations de production.
- Le login E2E utilise le vrai endpoint dans le navigateur automatisé, puis `sessionStorage['access_token']`. La route de connexion de l'application reste un placeholder ; aucun écran de login complet n'est revendiqué.
- GPS simulé dans le navigateur, pas de mesure physique. Le template Angular de démonstration reste présent avant le routeur.
- Le pipeline GitLab n'a pas été exécuté : aucune publication autorisée. Le CI existant compile le frontend, mais ne lance pas ses tests unitaires ni le nouveau scénario Chrome.
- La cible `develop` est une proposition fondée sur l'organisation locale ; le paramétrage et les critères GitLab officiels restent à confirmer.

## Préparation avant publication autorisée

Utiliser uniquement `docs/MR_US09_FILES.txt`. Exclure `frontend/angular.json` (analytics), les caches npm, les vrais `.env`, les profils Chrome, `target/`, `dist/`, les `.bak` et les changements d'autres services.

Pour `frontend/src/app/app.routes.ts`, inclure uniquement la nouvelle route US09 : les différences d'encodage des anciens libellés sont préexistantes et étrangères au ticket. Un patch sélectif `app.routes.us09.patch` est préparé localement dans `pharmacy-service/target/devops87-evidence/` ; il n'a pas été appliqué à l'index.

Le contenu est prêt pour revue et autorisation de publication. Aucun commit, push ni MR n'a été créé pendant la mission.
