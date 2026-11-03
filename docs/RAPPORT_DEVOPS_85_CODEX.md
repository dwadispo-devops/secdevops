# Rapport MédiDispo — DEVOPS-85

Date : 1 novembre 2026, fuseau Africa/Casablanca. Branche conservée : `feature/us09-availability-request`.

## 1. Résumé exécutif

**Verdict : PARTIELLEMENT VALIDÉ.** Le formulaire, le backend et PostgreSQL/PostGIS fonctionnent ensemble en local. La suite Maven finale passe **22 tests sans échec ni test ignoré** ; Angular passe **15 tests** et le build de production. Les POST réels, en direct et via le proxy Angular, produisent les données attendues dans une base isolée.

La connexion à un véritable compte patient via `auth-service` n'a pas été exécutée : les services d'authentification existants sont arrêtés. Les JWT utilisés sont signés localement avec la clé de cet environnement de validation, sans prétendre provenir d'un login réel. La géolocalisation a été testée avec des positions simulées dans les tests Angular ; aucune position physique de l'utilisateur n'a été demandée.

| Critère demandé | Statut | Preuve |
| --- | --- | --- |
| PostgreSQL/PostGIS fonctionnel | PASS | PostgreSQL 16.9, PostGIS 3.5.2 ; SQL et écritures HTTP vérifiés |
| Spring Boot démarre | PASS | Spring Boot 4.1.1 / Java 21 ; port 8082 ; health HTTP 200, `UP` |
| Migrations nécessaires exécutées | PASS | V1, V2 et V3 avec `success=true` dans `pharmacy_devops85` |
| Angular compile | PASS | `npm.cmd run build`, code 0 |
| Formulaire accessible | PASS | HTTP 200 et rendu Chrome headless de `/demande-disponibilite` ; route testée avec RouterTestingHarness |
| Frontend/backend reliés | PASS | POST sur 4200 transmis à 8082 : HTTP 201 ; données contrôlées en SQL |
| Tests pertinents | PASS | Maven 22/22 ; Angular 15/15 ; 18 contrôles HTTP de création, sécurité et validation |
| Aucune donnée importante supprimée | PASS | Aucun conteneur/volume préexistant supprimé ; base QA conservée, dix pharmacies et V1/V2 inchangées |
| Login patient réel puis soumission | BLOCKED | Authentification réelle non démarrée ; uniquement JWT de validation signés localement |
| Position GPS physique | BLOCKED | Succès/refus/absence de GPS simulés ; pas de mesure physique dans ce contexte |

Les preuves locales sont conservées sous `pharmacy-service/target/devops85-evidence/`, ignoré par Git. Aucun secret réel n'est reproduit dans ce rapport.

## 2. Audit Docker

Commandes réalisées : `docker ps -a`, `docker volume ls`, `docker inspect` de tous les conteneurs et `docker volume inspect` de tous les volumes. Les champs d'environnement enregistrés ont été limités à `POSTGRES_USER`, `POSTGRES_DB`, `PGDATA`, `DB_URL`, `DB_USER`.

L'inventaire final comprend **41 conteneurs et 17 volumes persistants** : 37 conteneurs préexistants et quatre conteneurs créés pendant cette mission. Seuls `medidispo-qa-pharmacy-db` et le nouveau backend sont actifs à la fin de l'audit. Les bases éphémères des tests ont été gérées par Testcontainers après autorisation explicite de l'utilisateur.

| Conteneur PostgreSQL préexistant | Configuration constatée par inspection | Décision |
| --- | --- | --- |
| `medidispo-qa-pharmacy-db` | `postgis/postgis:16-3.5`, actif, `127.0.0.1:5433`, rôle `qa_user`, base `pharmacy_qa`, volume `medidispo-qa-pharmacy-db-data` | Conservé ; cluster réutilisé pour une nouvelle base isolée |
| `medidispo-cnops-validation-db-20261015` | PostgreSQL 16, arrêté, port configuré 5436, `qa_user` / `medication_db`, volume dédié CNOPS | Conservé ; données réelles potentielles, aucune inspection SQL ni suppression |
| `medidispo-qa-auth-db-corrected` | PostgreSQL 16, arrêté, port configuré 5435, `qa_user` / `auth_qa_corrected`, volume dédié | Conservé |
| `medidispo-qa-postgres16-diagnostic` | PostgreSQL 16, arrêté, volume anonyme `b7f709…` | Conservé ; identité SQL non vérifiée |
| `medidispo-qa-medication-db` | PostgreSQL 16, arrêté, `qa_user` / `medication_qa`, volume dédié | Conservé |
| `secdevops-auth-db-1` | PostgreSQL 16, arrêté, `medi` / `authdb`, volume `secdevops_auth-db-data` | Conservé ; lié au Compose racine |
| `postgres-local` | PostGIS 15/3.4, arrêté, base déclarée `pharmacy_db`, volume anonyme `6b829f…` | Conservé ; données inconnues |

Pour les bases arrêtées, les noms ci-dessus sont des paramètres de création inspectés, **pas une affirmation sur leur contenu SQL actuel**. Seul le cluster actif a été interrogé. Les anciens services QA pointent vers leurs bases QA respectives ; rien ne prouve que leurs données soient jetables.

Volumes à conserver : les cinq volumes de données nommés MédiDispo, les deux volumes PostgreSQL anonymes attachés, le cache Maven partagé, les trois volumes du cluster `securek8s`, et `jenkins_home`. Le cache Maven est utilisé par 17 conteneurs à l'inventaire final. Les cinq autres volumes anonymes sans conteneur attaché restent inconnus : l'absence d'attachement ne prouve pas l'absence de données utiles.

**Nettoyage réalisé : aucune suppression.** Aucune commande `prune` exécutée. Aucun fichier de coéquipier, cache préexistant ou `.bak` supprimé. Les ressources Kubernetes et `hello-world`, extérieures à la mission, sont restées intactes.

**Liste proposée à examen, sans suppression :** anciens conteneurs de build `medidispo-tickets-*`, `medidispo-final-*`, `medidispo-cnops-dosage-build-*`, `medidispo-pwa-*`, et les trois nouveaux conteneurs de test/build arrêtés. Vérifier leurs artefacts avant toute suppression. Les anciens conteneurs PostgreSQL ne sont pas proposés à la suppression sans sauvegarde restaurée et autorisation explicite. Les cinq volumes anonymes non attachés nécessitent d'abord une identification de leur propriétaire et de leur contenu.

L'inventaire exhaustif et les dépendances précises sont dans `docker-audit.json` et `volume-dependencies.json` ; une liste lisible est annexée en fin de rapport.

### Docker Compose et environnement

- `docker-compose.yaml` définit Eureka, gateway et authentification ; il ne provisionne pas la base pharmacy. `auth-db` utilise le port 5432, pas 5433.
- `infra/docker-compose.yml` définit une base medication sur 5432, potentiellement en conflit avec celle du Compose racine si les deux sont démarrées ensemble. Son `infra/.env` est absent. Cette pile n'a pas été démarrée.
- Le fichier racine `.env` n'est pas automatiquement chargé par un processus Maven lancé dans `pharmacy-service`. Les valeurs des secrets n'ont pas été imprimées.
- `pharmacy-service/.env.example` décrit un environnement vierge `pharma` / `pharmacydb`, différent du cluster QA réel. Modifier `POSTGRES_USER` sur un volume initialisé ne recrée pas les rôles PostgreSQL.
- Les deux fichiers Compose partagés n'ont pas été modifiés.

## 3. Diagnostic PostgreSQL

La configuration Spring exige `spring.datasource.url=${DB_URL}`. Sans export de `DB_URL`, la valeur ne devient pas une URL JDBC valide. La seconde erreur était indépendante : le cluster actif n'a que le rôle `qa_user`, pas `pharma`. L'interrogation `psql -X -U qa_user -d pharmacy_qa` confirme ce rôle, la base et PostgreSQL 16.9. La base QA dispose de PostGIS 3.5.2, dix pharmacies et des migrations V1/V2.

Correction retenue : conserver `pharmacy_qa` et créer dans ce cluster **une nouvelle base `pharmacy_devops85` et un nouveau rôle applicatif non superutilisateur `pharma_devops85`**. Aucun mot de passe existant n'a été extrait ou réutilisé. Deux secrets aléatoires distincts ont été générés : mot de passe de 32 octets et clé JWT de 48 octets, encodés en base64. Ils sont uniquement dans `pharmacy-service/.env`, confirmé ignoré par `git check-ignore`.

Configuration locale :

```properties
DB_URL=jdbc:postgresql://localhost:5433/pharmacy_devops85
DB_USER=pharma_devops85
DB_PASSWORD=<secret local non reproduit>
JWT_SECRET=<secret local non reproduit>
```

PostGIS a été activé par l'administrateur uniquement dans cette nouvelle base. Flyway a ensuite créé V1/V2/V3 avec le compte applicatif. Les trois pharmacies et les demandes de validation sont exclusivement dans cette base isolée, conservée pour revue. La base d'origine conserve ses dix pharmacies et ses deux migrations.

Aucune sauvegarde QA n'a été créée : une tentative groupant dump et copie d'identifiants a été refusée avant exécution par le contrôle automatique. La solution retenue n'effectue aucune copie de données QA et n'en modifie pas le schéma. Une suppression future de données existantes exigerait toujours une sauvegarde restaurée et vérifiée ainsi qu'une autorisation.

## 4. Backend

Spring Boot **4.1.1**, compilation et exécution en **Java 21.0.12.1** via l'image déjà disponible `maven:3.9-eclipse-temurin-21`. Le Java Windows par défaut est en version 25 ; il n'a pas servi aux builds et à l'exécution validés.

Le backend tourne dans `medidispo-devops85-backend-20261101` et publie seulement `127.0.0.1:8082`. Son JAR provient de la suite Maven finale. Dans le conteneur, `DB_URL` vise `host.docker.internal:5433/pharmacy_devops85` ; la configuration `.env` pour un lancement Windows conserve `localhost:5433`.

`GET http://localhost:8082/actuator/health` retourne HTTP 200 avec `status=UP`. La connexion PostgreSQL est également prouvée par les écritures et les contrôles SQL ; le health seul n'a pas servi de preuve de persistance.

Correction ciblée du contrôleur : les coordonnées, le rayon et les quantités sont des types enveloppes permettant de distinguer une absence/null d'une valeur numérique ; les valeurs absentes et les coordonnées non finies sont rejetées avant écriture. Les tests d'intégration emploient désormais le vrai validateur JWT, avec vérification du statut initial `PENDING` et absence d'écriture en cas de rejet.

Le nouveau `start-local.ps1` importe explicitement `.env`, vérifie les quatre variables obligatoires et le préfixe JDBC, puis lance Maven ou un JAR fourni. Sa syntaxe PowerShell a été vérifiée sans erreur. Le mode Maven Windows n'a pas été exécuté ; le service validé tourne dans Docker avec Java 21.

## 5. Frontend DEVOPS-85

Les six fichiers demandés ont été inspectés avant modification. La route paresseuse, `provideHttpClient`, le stockage `sessionStorage['access_token']`, le header Bearer et l'essentiel du formulaire étaient déjà présents dans des modifications non commitées. Ils ont été préservés.

| Fonctionnalité | Statut et contrôle |
| --- | --- |
| Un ou plusieurs médicaments | PASS — ajout, suppression, conservation d'une ligne minimale et POST à deux médicaments |
| Quantités | PASS — validation d'entiers positifs ; quantités 2 et 1 constatées en SQL |
| Position GPS | PASS en simulation — succès, refus et API indisponible ; position physique BLOCKED |
| Rayon 100 à 20 000 m | PASS — bornes 100/20000 acceptées ; 99/20001, décimales et NaN rejetés côté formulaire |
| Validation avant envoi | PASS — noms blancs, liste vide, quantités fractionnaires, coordonnées non finies et rayon invalide |
| POST endpoint | PASS — corps JSON et header vérifiés dans les tests Angular ; requêtes réelles via proxy |
| JWT patient | PASS pour transmission et validation cryptographique locale ; login réel BLOCKED |
| Messages succès/erreur | PASS — tests des réponses 201, 401 et 500 |
| Route | PASS — RouterTestingHarness et Chrome headless |
| Proxy 4200 → 8082 | PASS — correction BOM puis 9 contrôles HTTP via 4200 |

Corrections minimales : garde contre les doubles clics pendant l'envoi, liste vide et coordonnées non finies rejetées, libellés accessibles sur les champs nom/quantité. Le fichier proxy était encodé avec BOM : `ng serve` échouait avec `[1, 1] InvalidSymbol`. Il est maintenant en UTF-8 sans BOM ; sa cible explicite `127.0.0.1:8082` correspond au port publié en IPv4.

Le serveur Angular fonctionne sur `http://127.0.0.1:4200/demande-disponibilite`. Le rendu Chrome contient `app-availability-request`, le titre du formulaire, le champ rayon et le bouton « Envoyer ma demande ». Le template Angular de démonstration présent dans `app.html` a été laissé intact pour limiter le périmètre ; il apparaît aussi avant le formulaire.

## 6. Tests exécutés

| Commande / vérification | Résultat | Preuve |
| --- | --- | --- |
| `git status --short` avant modifications | PASS | Modifications préexistantes identifiées, branche conservée |
| Maven `mvn -B -Dtest=AvailabilityJwtServiceTest,AvailabilityRequestControllerTest package` | PASS | 9 tests initiaux ; JAR construit |
| Maven `mvn -B verify`, première suite complète | PASS | 20 tests initiaux |
| Maven `mvn -B verify`, après corrections | PASS | **22 tests, 0 failure, 0 error, 0 skipped**, `BUILD SUCCESS` |
| `npm.cmd run build` dans `frontend` | PASS | Build production final ; bundle initial 279,47 kB |
| `npm.cmd test -- --watch=false` dans `frontend` | PASS | **2 fichiers, 15 tests** ; dont 13 cas du formulaire et les 2 tests App existants |
| `node pharmacy-service/target/devops85-evidence/http-checks.cjs` | PASS | 18 contrôles endpoint et health 200 ; `http-results.json` |
| Chrome headless `--dump-dom --virtual-time-budget=10000` sur la route | PASS | `browser-dom.html`, contrôles du titre/formulaire/bouton/rayon |
| SQL migrations, extension, demandes, médicaments et associations | PASS | `database-checks.txt` ; valeurs détaillées ci-dessous |
| Analyse syntaxique PowerShell de `start-local.ps1` | PASS | 0 erreur de parseur |
| `git diff --check` | PASS | Pas d'erreur ; simples avertissements Git LF/CRLF |

Répartition Maven finale : `PharmacyControllerIntegrationTest` 5 ; `PharmacyRepositoryTest` 4 ; `AvailabilityJwtServiceTest` 4 ; `AvailabilityRequestControllerTest` 5 ; `AvailabilityRequestIntegrationTest` 4.

Les tests Maven ont été lancés dans des conteneurs nommés, sources montées **en lecture seule**, copie de `pom.xml` et `src` dans `/tmp/build`, cache Maven partagé. Les tests Testcontainers ont utilisé le socket Docker seulement après accord explicite de l'utilisateur. Ils ont créé leurs propres bases PostGIS, sans utiliser les bases QA ou la base de démonstration.

Exécution reproductible du dernier build (utiliser un nouveau nom de conteneur lors d'une relance) :

```powershell
docker run -d --name medidispo-devops85-final-tests-20261101 `
  --mount 'type=bind,source=D:\oussama\mes-projet\secdevops\pharmacy-service,target=/source,readonly' `
  --mount 'type=volume,source=medidispo-qa-maven-cache,target=/root/.m2' `
  --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /tmp/build `
  maven:3.9-eclipse-temurin-21 sh -c 'cp -r /source/pom.xml /source/src . && mvn -B verify'
```

Incidents observés puis résolus : refus du premier lancement avec socket Docker, autorisé ensuite par l'utilisateur ; refus de duplication des données/identifiants QA, remplacée par un nouveau rôle/base sans copie ; démarrage Angular bloqué par le BOM, corrigé ; premier Chrome sandboxé bloqué par les permissions GPU, rendu ensuite vérifié avec un lancement autorisé ; une commande `npm run build` lancée par erreur à la racine a retourné ENOENT, puis le build a été relancé avec succès dans `frontend`. Ces tentatives ne sont pas comptées comme validations réussies.

## 7. Intégration frontend/backend

Le même corps à deux médicaments a été testé sur `http://localhost:8082/api/availability-requests` puis `http://127.0.0.1:4200/api/availability-requests` : latitude 31.63, longitude -8, rayon 2000 m, médicament fictif « DEVOPS85 Doliprane » quantité 2 et « DEVOPS85 Vitamine C » quantité 1.

| Cas | Direct 8082 | Proxy 4200 |
| --- | --- | --- |
| Aucun JWT | 401 | 401 |
| JWT avec signature d'une autre clé | 401 | 401 |
| JWT expiré | 401 | 401 |
| JWT de rôle PHARMACY | 401 | 401 |
| JWT PATIENT signé avec la clé locale | 201 | 201 |
| Latitude absente | 400 | 400 |
| Longitude null | 400 | 400 |
| Rayon 99 | 400 | 400 |
| Liste de médicaments vide | 400 | 400 |

Patient fictif : `85000000-0000-4000-8000-000000000085`. Les réponses 201 retournent son identifiant, un UUID de demande et `status=PENDING`. Aucun token n'est stocké dans les preuves HTTP.

Contrôle SQL réel : **2 demandes PENDING, 4 médicaments, 2 associations PENDING**. Les deux associations désignent uniquement « DEVOPS85 APPROVED proche » à la même position que le patient. « DEVOPS85 PENDING proche » et « DEVOPS85 APPROVED loin » n'ont aucune association. La pharmacie éloignée se trouve à 33.59/-7.62. Les données fictives restent dans la base isolée pour inspection.

Les requêtes HTTP réelles via proxy et les tests du composant sont deux vérifications complémentaires. Aucun scénario de clics navigateur avec login réel, GPS réel et soumission complète n'est revendiqué.

## 8. Fichiers modifiés

Liste exacte des fichiers créés ou modifiés par cette mission, hors preuves ignorées :

| Chemin | Changement |
| --- | --- |
| `frontend/src/app/pages/availability-request.ts` | Garde d'envoi, validation de liste/coordonnées, libellés accessibles ; fichier déjà présent non suivi |
| `frontend/src/app/pages/availability-request.spec.ts` | Nouveau fichier de tests du formulaire et de la route |
| `frontend/proxy.conf.json` | Suppression du BOM et cible IPv4 explicite ; fichier déjà présent non suivi |
| `pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestController.java` | Rejet des valeurs null/absentes et coordonnées non finies ; fichier déjà modifié avant mission |
| `pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestIntegrationTest.java` | Vrai JWT, PENDING, rejets et non-écriture ; conserve les tests préexistants |
| `pharmacy-service/.env.example` | Ajout de la variable JWT_SECRET obligatoire et explications, sans secret |
| `pharmacy-service/start-local.ps1` | Nouveau lanceur local PowerShell important `.env` sans afficher les secrets |
| `pharmacy-service/README.md` | Documentation PowerShell et distinction configuration/volume PostgreSQL initialisé |
| `docs/RAPPORT_DEVOPS_85_CODEX.md` | Ce rapport |

Fichier local ignoré créé : `pharmacy-service/.env`. Preuves/JAR/profil navigateur créés sous `pharmacy-service/target/devops85-evidence/`. Le build Angular écrit dans `frontend/dist` et ses caches habituels, ignorés par le dépôt frontend. Aucun cache ou secret ajouté à l'index Git.

État préexistant conservé sans modification par cette mission : `frontend/angular.json`, `frontend/package.json`, `frontend/src/app/app.config.ts`, `frontend/src/app/app.routes.ts`, `AvailabilitySecurityConfig.java`, `AvailabilityRequestControllerTest.java`, `AvailabilityPharmacySearchService.java`, `AvailabilityRequestPharmacyRepository.java`, `AvailabilityJwtServiceTest.java`, `frontend/.qa-npm-cache/`, le `.bak` de medication-service. Les trois derniers fichiers Java non suivis et les autres modifications existantes restent à revoir par leur auteur. Un `git diff` global comprend donc aussi des changements antérieurs à cette mission.

Aucun commit, push, rebase, merge, reset ou nettoyage Git exécuté.

## 9. Problèmes non résolus et reprise

1. **BLOCKED — véritable authentification patient.** Les conteneurs auth existants sont arrêtés et leurs clés n'ont pas été extraites. Pour conclure le parcours complet, préparer un environnement auth de test avec une base isolée, partager volontairement une clé JWT locale avec pharmacy, inscrire/connecter un patient puis placer son access token dans `sessionStorage['access_token']` sur l'origine du frontend. La page de connexion existante est un placeholder ; son intégration fonctionnelle n'a pas été inventée pendant ce ticket.
2. **BLOCKED — GPS physique.** Ouvrir la route sur le poste utilisateur et autoriser la localisation, puis vérifier une soumission réelle. Les cas GPS simulés passent déjà.
3. **À examiner — présentation racine.** Le template de démonstration Angular reste visible au-dessus du routeur. Le formulaire est rendu mais l'habillage global mérite un ticket séparé avec l'équipe.
4. **À examiner — historique Docker.** Bases anciennes et volumes non attachés conservés. Leur contenu et leur valeur métier restent inconnus ; aucune suppression autorisée ou réalisée.
5. **Configuration locale spécifique.** `pharmacy_devops85` est hébergée dans le cluster QA existant : isolation logique de données, pas un serveur PostgreSQL distinct. Le backend et Angular doivent être relancés après redémarrage du poste/Docker.

Relancer le backend de validation conservé : `docker start medidispo-devops85-backend-20261101`, avec `medidispo-qa-pharmacy-db` actif et le JAR local conservé. Relancer Angular depuis `frontend` avec `npm.cmd start -- --host 127.0.0.1 --port 4200`.

## 10. Verdict final DEVOPS-85

**PARTIELLEMENT VALIDÉ.** Les corrections locales sont appliquées et tous les tests exécutés après correction passent. Le fonctionnement PostgreSQL/PostGIS, Spring Boot, du formulaire Angular et de son proxy est démontré avec persistance réelle et sécurité JWT. La validation de bout en bout avec un compte patient authentifié et une position physique reste à effectuer ; une compilation Angular seule n'a pas été assimilée à une validation complète.

## 11. Prochaine étape recommandée — DEVOPS-86 / DEVOPS-87

Le libellé officiel de ces deux tickets n'est pas fourni dans le dépôt inspecté ni dans la mission ; aucune attribution fonctionnelle certaine n'est donc affirmée. Pour leur préparation, transmettre ce rapport et aligner avec leurs critères officiels : achever le parcours auth/patient/GPS puis réutiliser les demandes et associations PENDING validées ici pour tester le traitement côté pharmacies. Garder les fixtures et secrets dans l'environnement isolé, et inclure un véritable test navigateur du parcours final.

## Annexe — inventaire Docker final

Les chemins de montage et dépendances complètes sont dans les deux fichiers JSON locaux cités. Les conteneurs ci-dessous sont conservés ; aucun n'a été supprimé par la mission.

| Conteneur | Image | État final |
| --- | --- | --- |
| `medidispo-devops85-final-tests-20261101` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-devops85-backend-20261101` | `maven:3.9-eclipse-temurin-21` | running |
| `medidispo-devops85-integration-20261101` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-devops85-build-20261101` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-cnops-dosage-build-final-20261015` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-cnops-dosage-build-20261015` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-pwa-runtime-20261015` | `medidispo-pwa-browser-qa:20261015` | exited |
| `medidispo-pwa-browser-20261015` | `node:24-bookworm-slim` | exited |
| `medidispo-cnops-import-real-20261015` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-cnops-validation-db-20261015` | `postgres:16-bookworm` | exited |
| `medidispo-tickets-frontend-20261015` | `node:24-bookworm-slim` | exited |
| `medidispo-tickets-medication-service-658305` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-tickets-api-gateway-6faefb` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-tickets-pharmacy-service-f18c54` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-tickets-eureka-server-34bae8` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-tickets-prescription-service-2b0cd2` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-tickets-auth-service-d1a044` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-final-medication-service-fdbff5` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-final-api-gateway-fc7766` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-final-pharmacy-service-5f4470` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-final-eureka-server-816dd1` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-final-prescription-service-a76e5f` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-final-auth-service-439213` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-qa-auth-corrected` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-qa-auth-db-corrected` | `postgres:16-bookworm` | exited |
| `medidispo-qa-prescription-fast` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-qa-prescription` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-qa-medication` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-qa-postgres16-diagnostic` | `postgres:16` | exited |
| `medidispo-qa-pharmacy` | `maven:3.9-eclipse-temurin-21` | exited |
| `medidispo-qa-medication-db` | `postgres:16-bookworm` | exited |
| `medidispo-qa-pharmacy-db` | `postgis/postgis:16-3.5` | running |
| `secdevops-auth-service-1` | `auth-service:dev` | exited |
| `secdevops-auth-db-1` | `postgres:16-bookworm` | exited |
| `api-gateway` | `secdevops-api-gateway` | exited |
| `eureka-server` | `secdevops-eureka-server` | exited |
| `postgres-local` | `postgis/postgis:15-3.4` | exited |
| `vigorous_roentgen` | `hello-world` | exited |
| `securek8s-worker` | `kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5` | exited |
| `securek8s-worker2` | `kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5` | exited |
| `securek8s-control-plane` | `kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5` | exited |

| Volume | Conteneurs dépendants |
| --- | --- |
| `6b829f1024b7bdcc2ae1e07ea81a8399728a98bbf83b49e73d85a299dab0e2ff` | postgres-local |
| `8c224b574ac9c09abd57f2ef21bf3c342214cf6b82ea254b86c9ec3dd28f5c1b` | Aucun attachement constaté ; contenu inconnu |
| `32b726f7207284c0724bf6c25e88220f4787f979330b90f32677817ede243499` | Aucun attachement constaté ; contenu inconnu |
| `37fbf8c0cc6a4df118a2e303633cc36d0bd1e3f6c09df3c0f804525a80f9ae44` | Aucun attachement constaté ; contenu inconnu |
| `60ea590fcc6601232345059a2790d573811f18f95f1e9a735be231569dbd9143` | Aucun attachement constaté ; contenu inconnu |
| `65d84f529c5da3c98cfca8013a90f6a679e0879a3820012a7480edfd295398ee` | securek8s-control-plane |
| `96af3833652cfd2b8e447afa80e5a693963e5df2b0f5c3e3299698e174f7b596` | securek8s-worker |
| `a4b4c9c14e17aca640022cf95e7584b55184fdd5a74270d14f08cada96cd268c` | securek8s-worker2 |
| `b7f709cdc488f829745419e359f8907cab6e88f86e1dd562911f6cb9bee62204` | medidispo-qa-postgres16-diagnostic |
| `e2fb8e04be9aca2c574a96f8d013c526fd6484e630fe45457816dfb9f701cf13` | Aucun attachement constaté ; contenu inconnu |
| `jenkins_home` | Aucun attachement constaté ; contenu inconnu |
| `medidispo-cnops-validation-data-20261015` | medidispo-cnops-validation-db-20261015 |
| `medidispo-qa-auth-db-corrected-data` | medidispo-qa-auth-db-corrected |
| `medidispo-qa-maven-cache` | medidispo-devops85-final-tests-20261101, medidispo-devops85-integration-20261101, medidispo-devops85-build-20261101, medidispo-cnops-dosage-build-final-20261015, medidispo-cnops-dosage-build-20261015, medidispo-tickets-medication-service-658305, medidispo-tickets-api-gateway-6faefb, medidispo-tickets-pharmacy-service-f18c54, medidispo-tickets-eureka-server-34bae8, medidispo-tickets-prescription-service-2b0cd2, medidispo-tickets-auth-service-d1a044, medidispo-final-medication-service-fdbff5, medidispo-final-api-gateway-fc7766, medidispo-final-pharmacy-service-5f4470, medidispo-final-eureka-server-816dd1, medidispo-final-prescription-service-a76e5f, medidispo-final-auth-service-439213 |
| `medidispo-qa-medication-db-data` | medidispo-qa-medication-db |
| `medidispo-qa-pharmacy-db-data` | medidispo-qa-pharmacy-db |
| `secdevops_auth-db-data` | secdevops-auth-db-1 |
