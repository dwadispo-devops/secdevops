# Résolution locale des MR US09 et US11 — 3 novembre 2026

## Résultat et branches

- Base distante vérifiée par `git fetch origin develop` : `4681820`.
- US09 : `fix/us09-clean`, tête `eb946bd`, au-dessus de cette base.
- US11 : `fix/us11-clean`, au-dessus de US09, commit fonctionnel `5a469ca`, correctif `3afa77e`.
- `develop` locale n'a pas été déplacée ; utiliser la référence distante vérifiée pour la revue.
- Les branches feature et `backup/us11-before-fix`, les deux stashes et les fichiers personnels non suivis `kipping...` et `play this help.` ont été conservés.
- Aucun push, aucune fusion de MR, aucune suppression, aucun reset/clean.

## Résolutions US09

Le cherry-pick en cours de `ac59fc7` a été continué, sans annulation. Le premier commit déjà intégré `384c989` a été conservé.

- `frontend/proxy.conf.json` : UTF-8 sans BOM, JSON analysé avec Node ; `/api/**` vers `http://localhost:8080`, URL sans Markdown. Le motif couvre les sous-routes du serveur Angular actuel.
- `frontend/src/app/app.config.ts` : résolution marquée, avec routeur, HttpClient, gestion des erreurs et service worker conservés. Le contenu final est identique à develop.
- `frontend/src/app/app.routes.ts` : page US09 ajoutée, routes existantes conservées.
- `api-gateway/src/main/resources/application.properties` : route Eureka `lb://pharmacy-service` pour `/api/availability-requests` et ses sous-routes ; routes auth et pharmacies conservées.
- `pharmacy-service/README.md` : documentation develop conservée et instructions locales US09 ajoutées.
- `pharmacy-service/pom.xml` : dépendances develop et US09 conservées ; suppression d'une ligne vide avant la déclaration XML qui empêchait Maven de lire le POM.
- `pharmacy-service/src/main/resources/application.properties` : JWT, datasource, Flyway et Eureka conservés ; refus de la conversion des nombres décimaux en entiers rétabli.
- `pharmacy-service/src/test/resources/application.properties` : clé de test commune conservée, Eureka désactivé, même refus de conversion des nombres décimaux en entiers.
- `AvailabilitySecurityConfig.java` : suppression de la chaîne de repli `anyRequest().permitAll()` qui masquait la chaîne sécurisée develop. La chaîne spécifique US09 délègue toujours le contrôle du JWT patient au contrôleur ; les autres routes utilisent la sécurité develop.
- `AvailabilityRequestIntegrationTest.java` : signature des jetons à partir de la propriété de test réellement configurée, au lieu d'une autre clé codée en dur.
- `frontend/src/app/app.spec.ts` : ancien test de la page Angular par défaut adapté au composant racine contenant le routeur.

## US11 et cause des 401

La branche préparée `fix/us11-clean` contenait un seul commit propre US11 (`b0c3b2e`) sur origin/develop. Ce seul commit a été rebasé sur la tête US09 validée : aucun ancien commit US09 n'a été rejoué.

Avant correction, `StockControllerIntegrationTest` reproduit 4 échecs sur 5 : 401 au lieu de 204, 403 ou 400. Les jetons utilisaient une clé différente de `app.jwt.secret` ; le filtre develop refusait correctement leur signature avant l'accès au contrôleur.

La correction injecte la clé configurée dans le générateur de jetons des tests. Les contrôles de signature, rôle et propriété de la pharmacie restent actifs. Aucun changement de sécurité de production n'est nécessaire pour corriger ces 401.

Des tests supplémentaires vérifient une signature invalide (401), des corps JSON invalides (400 pour un pharmacien authentifié) et l'ordre des migrations Flyway. Les tests existants vérifient le propriétaire autorisé (204), le patient et le non-propriétaire (403), ainsi que le jeton absent (401).

## Migrations

Ordre validé par exécution PostgreSQL/PostGIS et lecture de `flyway_schema_history` :

1. V1 : pharmacies et extension PostGIS.
2. V2 : numéro de licence.
3. V3 : demandes de disponibilité et associations US09.
4. V4 : stock de pharmacie US11.
5. V5 : propriétaire de pharmacie US11.

Pas de version dupliquée ; V1 à V3 inchangées lors de l'intégration US11. Les tests démarrent sur une base jetable vierge ; aucune migration n'a été appliquée à une base personnelle ou de production.

## Vérifications exécutées

| Base / commande | Résultat |
| --- | --- |
| US09 : pharmacy-service `mvnw.cmd test` | 73 tests, 0 échec, 0 erreur, 0 ignoré |
| US09 : frontend `npm.cmd ci` | dépendances verrouillées installées |
| US09 : frontend `npm.cmd run build` | succès production |
| US09 : frontend `npm.cmd test -- --watch=false` | 27 tests réussis |
| US11 avant correction : `mvnw.cmd -Dtest=StockControllerIntegrationTest test` | 4 échecs 401 reproduits sur 5 tests |
| US09 + US11 : pharmacy-service `mvnw.cmd test` | 90 tests, 0 échec, 0 erreur, 0 ignoré |
| API Gateway : `mvnw.cmd test` | 1 test réussi, démarrage du contexte |
| Proxy : JSON.parse, URL et absence de syntaxe Markdown | succès |
| Fichiers XML, Java, TypeScript, Properties et JSON suivis : recherche de marqueurs Git | aucun marqueur |
| Ascendance Git | origin/develop ancêtre US09 ; US09 ancêtre US11 |

Maven exécuté avec le JDK complet IntelliJ (Java 25.0.4), compilation configurée pour Java 21. Le JAVA_HOME initial pointait sur un JRE sans javac ; seule la variable de processus a été adaptée. Docker Desktop a permis l'exécution réelle des tests Testcontainers PostGIS.

Angular n'a pas changé entre la tête US09 validée et US11. Avertissements de build existants : budget CSS pharmacies et dépendance Leaflet CommonJS. npm ci signale 6 vulnérabilités de dépendances ; aucune mise à jour hors périmètre n'a été faite. Les tests navigateur de bout en bout et un routage réel via Eureka ne sont pas couverts par ces commandes.

Journaux locaux ignorés par Git : `us09-maven.log`, `us09-angular-build.log`, `us09-angular-test.log`, `npm-ci.log`, `us11-before-fix.log`, `us11-maven.log`, `gateway-maven.log`.

## Commits

- `384c989` : premier commit US09, déjà présent avant intervention.
- `ca7990d` : cherry-pick US09 terminé et conflits résolus.
- `eb946bd` : POM XML et configuration des tests US09 corrigés.
- `5a469ca` : unique commit US11 replacé sur la base US09.
- `3afa77e` : correction JWT des tests US11 et tests de sécurité/Flyway.
- Le commit du présent rapport contient aussi une normalisation de fin de fichier dans StockJwtService.java ; consulter `git log` pour son hash.

## Étapes restantes GitLab

1. Relire les deux branches locales. Les branches source feature existantes n'ont pas été modifiées : les MR distantes ne sont donc pas encore mises à jour.
2. Après autorisation de publication, publier les branches fix sans force. Utiliser des MR de remplacement depuis ces branches vers develop, ou convenir explicitement d'une autre stratégie de mise à jour des sources existantes. Ne pas fusionner l'ancien historique en conflit dans les branches propres.
3. Valider puis fusionner US09 en premier. US11 dépend de US09 et ne doit pas être fusionnée avant.
4. Après fusion US09, récupérer develop. Si GitLab a conservé les commits US09, la différence US11 ne contient que son travail. En cas de squash/rebase GitLab, replacer uniquement les commits après l'ancienne tête US09 `eb946bd` sur le nouveau develop, puis rejouer les tests avant publication. Ne pas rejouer les anciens commits US09.
5. Exécuter les pipelines GitLab et la recette métier ; fusionner US11 après validation humaine.

## Fichiers concernés

Liste US09 par rapport à origin/develop :

- api-gateway/src/main/resources/application.properties
- docs/MR_US09_DESCRIPTION.md
- docs/MR_US09_FILES.txt
- docs/RAPPORT_DEVOPS_85_CODEX.md
- docs/RAPPORT_DEVOPS_86_CODEX.md
- docs/RAPPORT_DEVOPS_87_CODEX.md
- frontend/package.json
- frontend/proxy.conf.json
- frontend/src/app/app.routes.ts
- frontend/src/app/app.spec.ts
- frontend/src/app/pages/availability-request.spec.ts
- frontend/src/app/pages/availability-request.ts
- pharmacy-service/.env.example
- pharmacy-service/README.md
- pharmacy-service/pom.xml
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityJwtService.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityPharmacySearchService.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequest.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestController.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestItem.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestItemRepository.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestPharmacy.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestPharmacyRepository.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestRepository.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilitySecurityConfig.java
- pharmacy-service/src/main/resources/application.properties
- pharmacy-service/src/main/resources/db/migration/V3__create_availability_requests.sql
- pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/availability/AvailabilityJwtServiceTest.java
- pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestControllerTest.java
- pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestIntegrationTest.java
- pharmacy-service/src/test/resources/application.properties
- pharmacy-service/start-local.ps1
- scripts/us09-e2e/browser.cjs
- scripts/us09-e2e/fixtures.sql
- scripts/us09-e2e/run-browser.ps1

Liste US11 par rapport à la tête US09 :

- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/pharmacy/Pharmacy.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/stock/StockController.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/stock/StockJwtService.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/stock/StockRepository.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/stock/StockService.java
- pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/stock/StockStatus.java
- pharmacy-service/src/main/resources/db/migration/V4__create_pharmacy_stock.sql
- pharmacy-service/src/main/resources/db/migration/V5__add_pharmacy_owner.sql
- pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/stock/StockControllerIntegrationTest.java
- pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/stock/StockJwtServiceTest.java
- pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/stock/StockRepositoryIntegrationTest.java
- pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/stock/StockServiceTest.java
