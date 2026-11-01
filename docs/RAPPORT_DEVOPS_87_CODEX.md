# MédiDispo US09 — DEVOPS-87

Date : 1 novembre 2026, fuseau Africa/Casablanca. Dépôt : `D:\oussama\mes-projet\secdevops`.

## 1. Résumé exécutif

**DEVOPS-87 : VALIDÉ dans le périmètre de la mission fournie.** Un véritable compte PATIENT a été inscrit puis authentifié auprès d'auth-service depuis Chrome headless. Le JWT émis par ce service a permis une soumission du formulaire Angular via son proxy, avec réponse 201, confirmation affichée et persistance SQL vérifiée. Les trois cas d'erreur 400, 401 et 500 ont également été exécutés depuis le navigateur, sans écritures supplémentaires.

Régression reconfirmée : **61 tests Maven réussis, 0 échec, 0 erreur, 0 ignoré ; 27 tests Angular réussis, 0 échec, 0 ignoré ; build Angular de production réussi.** L'automatisation navigateur valide **4 scénarios (succès, 400, 401, 500), avec 9 points de contrôle réussis, 0 échec et 0 ignoré** lors de sa dernière exécution. Les 9 contrôles ne sont pas présentés comme 9 tests JUnit supplémentaires.

La MR est **préparée pour revue et publication après autorisation**, avec titre, description, liste de fichiers et patch sélectif de la route. Aucun commit, push, création de MR, merge, rebase, reset ou nettoyage Git n'a été effectué. Aucune démonstration visuelle interactive n'a été lancée.

## 2. État initial du dépôt

`git status --short` et `git branch --show-current` ont été exécutés avant modification. La branche est bien `feature/us09-availability-request`. Les rapports DEVOPS-85 et DEVOPS-86 et les sources réelles ont été lus.

Le commit HEAD existant `6bad6be` ajoute l'API US09 et référence DEVOPS-79/80/81. La comparaison locale `git diff --name-status origin/develop...HEAD` montre 14 fichiers déjà commités de pharmacy-service, dont les entités, la sécurité, les dépendances JWT et la migration V3. Les références distantes locales n'ont pas été actualisées : aucune affirmation n'est faite sur l'état courant du serveur GitLab.

L'arbre de travail comportait déjà les changements DEVOPS-85/86, plusieurs fichiers US09 non suivis, un cache `frontend/.qa-npm-cache/`, un `.bak` dans medication-service, une modification analytics dans `frontend/angular.json`, et des différences d'encodage de libellés existants dans `app.routes.ts`. Tout ce travail a été préservé. L'index était vide de changements et n'a pas été modifié.

**Critères officiels : indisponibles.** La recherche dans le dépôt ne fournit pas de fiche DEVOPS-87. L'appel anonyme réel à `https://gitlab.com/api/v4/projects/secdevops-projet%2Fsecdevops/issues?search=DEVOPS-87` retourne HTTP 404. Cela ne prouve pas l'absence du ticket : le projet peut être privé ou le ticket suivi ailleurs. Aucun critère officiel ni libellé des autres tickets n'a été inventé. Le présent verdict évalue les exigences de la mission utilisateur.

## 3. Environnement de validation isolé

| Élément | Configuration réellement utilisée |
| --- | --- |
| Réseau Docker nouveau | `medidispo-devops87` |
| PostgreSQL/PostGIS nouveau | `medidispo-devops87-db`, image `postgis/postgis:16-3.5` ; aucun port publié vers l'hôte |
| Volume nouveau | `medidispo-devops87-data-20261101` ; conservé |
| Bases nouvelles | `auth_e2e` et `pharmacy_e2e` ; aucune donnée copiée d'une base existante |
| Compte de base nouveau | `e2e_owner`, uniquement pour cette pile de validation |
| Authentification | `medidispo-devops87-auth`, hôte `127.0.0.1:28081` vers 8081 |
| Pharmacy | `medidispo-devops87-pharmacy`, hôte `127.0.0.1:28082` vers 8082 |
| Angular | Processus Node caché, `127.0.0.1:24200` |
| Proxy E2E | `/api/auth` vers 28081 ; `/api/availability-requests` vers 28082 |
| Navigateur | Chrome installé, uniquement headless ; CDP `127.0.0.1:29222`, profil dédié sous `target/` |
| Runtime backend | Java Temurin 21, Spring Boot 4.1.1 |

Les JAR ont été compilés à partir des sources actuelles montées en lecture seule dans des conteneurs Maven. Le JAR pharmacy provient du `mvn -B verify` de régression ; auth-service a été compilé avec `mvn -B -DskipTests package`. Les tests unitaires d'auth-service n'ont pas été exécutés dans cette mission ; son inscription/login réels ont été exercés par l'E2E.

Un nouveau mot de passe de base et une nouvelle clé JWT aléatoire ont été générés pour cette pile ; auth et pharmacy partagent volontairement cette seule nouvelle clé. Aucun secret ni mot de passe de service existant n'a été extrait. Les fichiers `database.env`, `services.env` et `patient.env` sont sous `pharmacy-service/target/devops87-evidence/`, ignoré par Git. Les JWT ne sont ni enregistrés dans les résultats ni affichés.

Les services existants sur 8082, 4200 et le PostgreSQL QA sur 5433 n'ont pas été arrêtés ou redémarrés. Les données anciennes sont restées intactes ; seules les nouvelles bases E2E ont reçu les écritures de ce parcours.

## 4. Parcours E2E exécuté étape par étape

Automatisation livrée : `scripts/us09-e2e/browser.cjs`, sans dépendance navigateur additionnelle, utilisant le WebSocket/CDP de Node 24. Le lanceur PowerShell `run-browser.ps1` démarre uniquement Chrome headless si nécessaire. Les fixtures et la panne contrôlée sont dans `fixtures.sql`, avec garde exigeant la base `pharmacy_e2e`.

| Étape demandée | Résultat et preuve automatisée |
| --- | --- |
| 1. Authentification réelle PATIENT | PASS — inscription `/api/auth/register` HTTP 201, rôle PATIENT, puis `/api/auth/login` HTTP 200 ; appels réels exécutés dans le navigateur via le proxy |
| 2. JWT réel vérifié par pharmacy | PASS — l'access token retourné par auth-service est placé dans `sessionStorage['access_token']` ; pharmacy accepte ensuite le POST sans token signé artificiellement par le test |
| 3. Route Angular | PASS — attente du vrai DOM `app-availability-request form` sur `/demande-disponibilite` |
| 4. Plusieurs médicaments/quantités | PASS — clic Ajouter puis événements `input` : `DEVOPS87 Paracetamol` quantité 2 et `DEVOPS87 Vitamine C` quantité 3 |
| 5. GPS navigateur | PASS — permission géolocalisation et `Emulation.setGeolocationOverride` : latitude 31.63, longitude -8, précision 1 m ; clic GPS puis attente du message de récupération |
| 6. Rayon | PASS — saisie réelle dans le champ rayon : 2000 m |
| 7. Soumission via proxy | PASS — clic sur le bouton Envoyer ; Angular HttpClient envoie la vraie requête, observée par `Network.responseReceived` sur l'origine 24200 |
| 8. HTTP 201 | PASS — statut de la réponse de cette soumission contrôlé dans CDP |
| 9. Demande en PostgreSQL | PASS — SELECT de la demande pour l'UUID du patient inscrit, patient/coordonnées/rayon contrôlés |
| 10. Médicaments associés | PASS — SELECT des deux noms et quantités liés à l'UUID de la demande ; égalité exacte |
| 11. Pharmacie APPROVED proche | PASS — seule `DEVOPS87 APPROVED proche` est associée |
| 12. Exclusions | PASS — résultat exact à une association, excluant `DEVOPS87 PENDING proche` et `DEVOPS87 APPROVED loin` |
| 13. PENDING initial | PASS — statut de la demande et de l'association relus en SQL |
| 14. Confirmation Angular | PASS — attente du message de succès dans `[role=status]` après la vraie réponse 201 |

Le login est effectué par un appel au vrai endpoint depuis la page, et non par un écran de connexion Angular : la route de connexion reste un placeholder. Il n'y a ni faux JWT ni contournement de la signature du backend. Le GPS est simulé dans le navigateur lui-même, et non injecté directement dans l'objet du composant.

La soumission de succès, les messages 401/500 et les contrôles SQL ont été validés une première fois avec le script Node, puis une deuxième fois via le lanceur PowerShell livré, avec de nouveaux patients fictifs. Les deux exécutions réussies ne sont pas additionnées au nombre de scénarios finaux.

## 5. Résultats HTTP et SQL

| Cas navigateur | HTTP constaté | Angular / SQL |
| --- | ---: | --- |
| Inscription fictive PATIENT | 201 | UUID et rôle PATIENT contrôlés ; mot de passe traité par le vrai service |
| Login réel | 200 | Bearer reçu, jamais imprimé |
| Soumission réelle du formulaire | 201 | Confirmation de succès ; 1 demande, 2 médicaments, 1 association par scénario |
| Rayon invalide 99 | 400 | Appel `fetch` réel depuis le navigateur avec JWT réel ; les trois comptes SQL restent inchangés |
| Token invalide | 401 | Soumission du vrai formulaire avec un token invalide ; message de session invalide/expirée ; aucune écriture |
| Erreur backend contrôlée | 500 | Soumission du vrai formulaire avec le médicament fictif `DEVOPS87_FORCE_500` ; message d'échec ; transaction annulée, comptes SQL inchangés |

Le cas 400 contourne volontairement la validation du formulaire par un appel API depuis le navigateur pour atteindre la validation backend. Il n'est pas présenté comme un formulaire valide qui recevrait un 400.

La panne 500 provient d'un trigger PostgreSQL exclusivement installé dans la nouvelle base de validation. Il lève une exception seulement pour le marqueur de test, sans changement du code métier ni des migrations Flyway. Cette exception réelle de persistance doit annuler toute la transaction. Les trois comptes demandes/médicaments/associations sont comparés avant et après chaque erreur.

Fixtures géographiques : APPROVED proche et PENDING proche à 31.63/-8 ; APPROVED loin à 33.59/-7.62. Le résultat SQL exact ne contient que la pharmacie APPROVED proche, avec association PENDING. Après les deux scénarios de succès exécutés, la base E2E contient deux demandes de patients fictifs distincts, quatre médicaments et deux associations ; aucun cas d'erreur ne doit ajouter de ligne.

Preuves locales : `e2e-results.json` conserve les UUID fictifs de la dernière exécution, les données attendues et les 9 contrôles PASS ; `sql-results.txt` conserve les SELECT réels ; `maven-summary.txt` et `surefire/` conservent les résultats de régression. Aucun JWT ni mot de passe n'est inclus dans ces résultats.

## 6. Régression Maven et Angular

| Suite / commande exécutée | Réussis | Échoués | Erreurs | Ignorés |
| --- | ---: | ---: | ---: | ---: |
| Maven pharmacy `mvn -B verify` sous Java 21 avec Testcontainers | 61 | 0 | 0 | 0 |
| Angular `npm.cmd test -- --watch=false` dans `frontend` | 27 | 0 | 0 | 0 |
| **Total JUnit/Vitest** | **88** | **0** | **0** | **0** |

Répartition Maven : PharmacyControllerIntegrationTest 5 ; PharmacyRepositoryTest 4 ; AvailabilityJwtServiceTest 8 ; AvailabilityRequestControllerTest 11 ; AvailabilityRequestIntegrationTest 33. `BUILD SUCCESS`, durée Maven 28,316 s. Les rapports XML Surefire sont récupérés du nouveau conteneur `medidispo-devops87-regression-20261101`.

Le conteneur de régression emploie `maven:3.9-eclipse-temurin-21`, les sources en lecture seule sous `/source`, une copie dans `/tmp/build`, le cache Maven partagé, le socket Docker déjà autorisé pour Testcontainers et `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. Commande interne : `cp -r /source/pom.xml /source/src . && java -version && mvn -B verify`. Les bases de ces tests sont temporaires et distinctes de la pile E2E conservée.

`npm.cmd run build` a aussi été exécuté avec succès : bundle initial 279,47 kB. Les 27 tests Angular et le build ont été relancés sans modification du code frontend pendant DEVOPS-87.

Autres vérifications exécutées : `node --check scripts/us09-e2e/browser.cjs`, analyse syntaxique PowerShell du lanceur (0 erreur), exécution réelle du lanceur headless, `git diff --check`, vérification des migrations et lectures SQL de préservation.

## 7. Bugs trouvés et corrections

Aucun nouveau défaut métier n'a été trouvé pendant DEVOPS-87. Les corrections JWT et désérialisation numérique de DEVOPS-86 sont incluses dans le JAR actuel testé.

Deux problèmes du harnais E2E ont été rencontrés puis corrigés avant les succès : le premier lancement arrivait avant que DevTools soit prêt (`fetch failed`), puis les sélecteurs fondés sur `input[name=...]` ne correspondaient pas au DOM des champs dynamiques gérés par ngModel. Le script attend désormais explicitement DevTools et la nouvelle ligne, puis sélectionne les champs effectivement rendus. Aucun changement métier n'a été effectué pour faire passer le test. Les tentatives en échec n'ont pas produit de demande validée ; leurs résultats ne sont pas comptés comme des succès.

L'audit Git identifie aussi deux différences préexistantes étrangères au périmètre à publier : analytics dans `frontend/angular.json` et libellés mal encodés des anciennes routes. Ces fichiers/hunks n'ont pas été effacés ou réécrits. Un patch de la seule nouvelle route est fourni pour éviter de publier les changements d'encodage.

## 8. Fichiers modifiés pendant DEVOPS-87

Fichiers nouveaux destinés à la revue :

1. `scripts/us09-e2e/browser.cjs` — automatisation réelle Chrome/CDP, login, formulaire, HTTP et SQL, sans dépendance additionnelle.
2. `scripts/us09-e2e/fixtures.sql` — fixtures réservées à pharmacy_e2e et panne backend contrôlée.
3. `scripts/us09-e2e/run-browser.ps1` — lanceur exclusivement headless.
4. `docs/MR_US09_DESCRIPTION.md` — titre et description proposés, tests et limites.
5. `docs/MR_US09_FILES.txt` — liste explicite de fichiers US09, incluant ceux déjà commités sur la branche.
6. `docs/RAPPORT_DEVOPS_87_CODEX.md` — ce rapport.

Aucun fichier métier ni configuration partagée existante n'a été modifié pendant cette mission. Les rapports DEVOPS-85/86 et les changements des coéquipiers restent préservés.

Les JAR, secrets de la nouvelle pile, proxy E2E, logs, profil Chrome, résultats et patch sélectif ont été créés uniquement sous `pharmacy-service/target/devops87-evidence/`, ignoré par Git. Les sorties habituelles du build frontend restent ignorées.

## 9. État Git et préparation MR

Le périmètre proposé est explicitement listé dans `docs/MR_US09_FILES.txt`. Il combine l'API déjà commitée et les compléments non commités de formulaire, associations, validation, tests et documentation.

Exclusions vérifiées : vrais `.env`, `database.env`, `services.env`, `patient.env`, caches npm, `.bak`, `target/`, `dist/`, profils navigateur, modification analytics et modifications d'autres services. Ne pas utiliser un ajout global de l'arbre de travail.

`app.routes.ts` mélange l'ajout US09 et des différences préexistantes d'encodage sur d'autres routes. Le patch `pharmacy-service/target/devops87-evidence/app.routes.us09.patch` a été généré à partir de HEAD et contient uniquement les cinq lignes de la nouvelle route, avec le contexte d'origine. Il n'a pas été appliqué à l'index : le travail existant reste intégralement présent.

`git diff --check` ne signale aucune erreur ; seuls des avertissements LF/CRLF de Git sont présents. `git apply --check --cached` valide le patch sélectif sans l'appliquer ni modifier l'index. La présence des fichiers de la liste et l'absence des secrets locaux réels dans leur contenu ont été contrôlées. Les migrations V1/V2/V3 n'ont aucune différence par rapport à HEAD, leurs versions sont uniques et ordonnées, et Flyway a validé puis appliqué les trois migrations avec `success=true` sur la nouvelle base PostGIS. Le trigger de test ne fait pas partie des migrations.

**État de préparation : contenu prêt pour revue ; publication non effectuée, en attente d'autorisation explicite.** La cible `develop` est proposée d'après les références et l'historique locaux, pas confirmée par les paramètres GitLab. Il faudra contrôler les changements distants et la cible officielle au moment d'une publication autorisée. Aucun conflit distant ni pipeline GitLab réussi n'est revendiqué.

Le CI existant contient des jobs Maven avec Docker-in-Docker et un build frontend ; il ne lance pas les tests Angular ni le nouveau scénario Chrome. La validation locale de ce rapport ne remplace pas une exécution CI ultérieure.

## 10. Titre et description proposés pour la MR

**Titre :** `feat(us09): demander la disponibilité de médicaments auprès des pharmacies proches`

**Description :** le texte prêt à copier est dans `docs/MR_US09_DESCRIPTION.md`. Il décrit le formulaire multi-médicaments, GPS/rayon, JWT PATIENT, persistance transactionnelle, filtrage APPROVED et PENDING initial. Il référence **DEVOPS-79 à DEVOPS-87**, les 61 + 27 tests, le vrai parcours navigateur, les erreurs 400/401/500 et les limites. Les libellés officiels inconnus ne sont pas inventés.

## 11. Blocages et limites

- Aucun blocage technique restant pour le parcours automatisé demandé : le login réel, le navigateur, les erreurs et les régressions ont été exécutés.
- Critères officiels GitLab non accessibles (404 anonyme). Confirmation de la cible de MR et pipeline distant à réaliser lors de la publication autorisée.
- Aucun écran de login Angular complet ; login E2E via endpoint réel dans le navigateur et stockage de l'access token selon le contrat actuel.
- GPS simulé, pas de mesure physique ; aucune démonstration interactive exécutée.
- Pas de tests de charge, concurrence ni validation fonctionnelle d'une diffusion à plus de 100 pharmacies.
- La pile E2E et ses données fictives sont conservées. Les bases anciennes arrêtées n'ont pas été inspectées ou démarrées ; leur contenu n'est pas déclaré jetable.

## 12. Verdict DEVOPS-87 et préservation

**VALIDÉ.** Les 14 étapes de la mission, les trois scénarios d'erreur reproductibles et les suites DEVOPS-86 ont été exécutés avec des preuves réelles. La MR est préparée, sans publication.

Les empreintes des lignes de `pharmacies` et les historiques Flyway de `pharmacy_qa` et `pharmacy_devops85` sont identiques avant/après. Ces comparaisons ne sont pas présentées comme un hash exhaustif de toutes les bases du poste. Le contrôle final constate zéro conteneur préexistant manquant, et la seule différence de volumes est l'ajout de `medidispo-devops87-data-20261101`. `pharmacy_devops85` conserve ses 2 demandes, 4 médicaments et 2 associations. Aucun conteneur ni volume préexistant n'a été supprimé. Aucun prune ni suppression destructive n'a été exécuté.

## 13. Démarrage pour une démonstration manuelle ultérieure

Ces instructions sont préparées pour plus tard ; **aucune fenêtre de démonstration interactive n'a été ouverte**. Les services de validation sont déjà actifs au moment de ce rapport.

1. Si Docker a été redémarré, relancer uniquement les ressources dédiées : `docker start medidispo-devops87-db`, puis `docker start medidispo-devops87-auth medidispo-devops87-pharmacy`. Ne pas supprimer leur volume. Les JAR montés sous `target/devops87-evidence/` doivent être conservés.
2. Si le serveur Angular dédié n'est plus actif, depuis `frontend` :

   ```powershell
   node node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1 --port 24200 --proxy-config ../pharmacy-service/target/devops87-evidence/proxy.json
   ```

3. Ouvrir ultérieurement `http://127.0.0.1:24200/demande-disponibilite`. Le backend dédié est sur `http://127.0.0.1:28082/actuator/health` ; auth-service utilise le port 28081. Les services historiques sur 8082/4200 ne sont pas la pile décrite ici.
4. Le compte fictif de la dernière exécution est conservé dans `patient.env` sous le dossier local ignoré. Ne pas publier ses valeurs. Comme l'écran de connexion reste un placeholder, utiliser ce compte par l'endpoint de login sur la même origine frontend et enregistrer l'access token dans sessionStorage. Exemple à exécuter plus tard dans la console du navigateur, sans afficher le token :

   ```javascript
   await (async () => {
     const response = await fetch('/api/auth/login', {
       method: 'POST', headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify({ email: prompt('E-mail fictif de test'), password: prompt('Mot de passe fictif de test') })
     });
     if (!response.ok) throw new Error('Connexion refusée');
     const body = await response.json();
     sessionStorage.setItem('access_token', body.accessToken);
   })();
   ```

5. Pour reproduire les fixtures géographiques, utiliser plus tard une position contrôlée 31.63/-8 dans les outils Sensors du navigateur et autoriser la géolocalisation. Sinon une position physique différente peut légitimement ne trouver aucune pharmacie fictive proche. Ajouter deux médicaments, quantités 2/3, rayon 2000, puis envoyer.
6. Attendre la confirmation, puis inspecter seulement la base dédiée : `docker exec medidispo-devops87-db psql -X -U e2e_owner -d pharmacy_e2e`. Vérifier demandes/médicaments/associations. Le marqueur `DEVOPS87_FORCE_500` est réservé au test de panne et ne doit pas servir de médicament de démonstration normale.
7. Pour relancer uniquement l'automatisation sans fenêtre interactive : `./scripts/us09-e2e/run-browser.ps1`. Elle crée un nouveau patient fictif et une nouvelle demande, conserve les précédents et réécrit le résultat local de la dernière exécution. Les fixtures doivent déjà être installées et les services dédiés actifs.
