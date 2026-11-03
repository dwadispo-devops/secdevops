# MédiDispo US09 — Rapport DEVOPS-86

Date : 1 novembre 2026, Africa/Casablanca. Dépôt : `D:\oussama\mes-projet\secdevops`. Branche conservée : `feature/us09-availability-request`.

## Verdict

**VALIDÉ — DEVOPS-86 : ajouter les tests unitaires et d'intégration.** Les suites existantes ont été exécutées avant modification, les scénarios manquants ont été ajoutés, trois erreurs ont été reproduites puis corrigées, et les vérifications finales passent réellement.

| Validation finale | Réussis | Échecs d'assertion | Erreurs d'exécution | Ignorés |
| --- | ---: | ---: | ---: | ---: |
| Maven, Java 21, PostgreSQL/PostGIS Testcontainers | 61 | 0 | 0 | 0 |
| Angular / Vitest | 27 | 0 | 0 | 0 |
| **Total des suites finales** | **88** | **0** | **0** | **0** |

Le build Angular de production est également **PASS**. Les exécutions de référence et intermédiaires ne sont pas additionnées au total final.

Ce verdict porte sur les tests unitaires et d'intégration demandés. Il ne vaut pas validation d'un parcours utilisateur complet avec login réel, GPS physique et navigateur ; ces limites de DEVOPS-85 subsistent pour une future validation de bout en bout.

## Audit initial et tests existants

`git status --short` et `git branch --show-current` ont été exécutés avant toute modification. Le rapport `docs/RAPPORT_DEVOPS_85_CODEX.md`, les tests réels, le contrôleur US09, le validateur JWT, la recherche géographique, les migrations et les configurations Maven/Angular ont été inspectés. Aucune consigne `AGENTS.md` n'a été trouvée par la recherche dans le dépôt.

Le dépôt contenait déjà de nombreux changements non commités, des fichiers non suivis, un cache npm QA et un `.bak` de medication-service. Ces éléments ont été préservés. Le rapport DEVOPS-85 n'a pas été modifié.

Les nombres annoncés dans DEVOPS-85 ont été **confirmés par de nouvelles exécutions** : Maven 22 réussis, 0 échec, 0 erreur, 0 ignoré ; Angular 15 réussis, 0 échec, 0 ignoré.

| Classe / fichier | Référence confirmée | Ajouts DEVOPS-86 | Suite finale |
| --- | ---: | ---: | ---: |
| `PharmacyControllerIntegrationTest` | 5 | 0 | 5 |
| `PharmacyRepositoryTest` | 4 | 0 | 4 |
| `AvailabilityJwtServiceTest` | 4 | 4 | 8 |
| `AvailabilityRequestControllerTest` | 5 | 6 | 11 |
| `AvailabilityRequestIntegrationTest` | 4 | 29 | 33 |
| **Maven** | **22** | **39** | **61** |
| `app.spec.ts` | 2 | 0 | 2 |
| `availability-request.spec.ts` | 13 | 12 | 25 |
| **Angular** | **15** | **12** | **27** |

Les invocations paramétrées sont comptées individuellement par JUnit/Vitest. Certains tests existants vérifient plusieurs assertions ou requêtes dans une seule invocation ; ces assertions ne sont pas comptées comme des tests distincts.

L'audit a notamment révélé que le test de rôle existant utilisait `PHARMACY`, alors que l'enum réel du service d'authentification contient `PATIENT`, `PHARMACIEN` et `ADMIN`. Le test existant a été conservé et les deux rôles réels non patients sont désormais couverts.

## Nouveaux tests et couverture US09

### Backend

- **20 cas HTTP d'entrées invalides**, chacun exigeant HTTP 400 et aucun changement dans les trois tables demandes/médicaments/associations : latitude et longitude hors limites dans les deux directions, rayon 99 ou 20001, rayon décimal, quantité décimale, nulle au sens numérique (zéro), négative ou absente, nom blanc/absent/null/trop long, élément médicament null, liste vide/null, corps null et JSON malformé. Les cas null/absents supplémentaires déjà présents dans DEVOPS-85 sont conservés.
- **4 cas HTTP de rôles non autorisés** : `PHARMACIEN`, `ADMIN`, `patient` en minuscules et rôle vide/absent dans le JWT généré. Réponse 401 et aucune écriture dans les trois tables.
- **1 cas HTTP de JWT patient signé sans sujet** : réponse 401 et aucune écriture.
- **2 cas d'intégration aux bornes de rayon 100 et 20000 m** : plusieurs médicaments, nom à la longueur maximale 200, quantités exactes, patient provenant du JWT, statut de demande `PENDING` relu en base. Une pharmacie APPROVED à la position du patient et une APPROVED à rayon moins un mètre sont associées en `PENDING` ; celle à rayon plus un mètre, une APPROVED éloignée et une PENDING proche sont exclues. Exactement deux associations sont attendues. Les positions à proximité du rayon sont construites avec `ST_Project` sur une géographie PostGIS, en mètres.
- **1 cas sans pharmacie proche** : demande et médicament créés, statut PENDING, aucune association.
- **1 cas d'annulation transactionnelle** : une exception est injectée dans la recherche après la sauvegarde de la demande et des médicaments. Les comptes des trois tables doivent rester identiques. Le test n'est pas lui-même enveloppé dans une transaction de test qui masquerait le comportement du contrôleur. Le service de recherche espionné reste réel dans les autres tests ; sa substitution est réinitialisée par le support Mockito de Spring.
- **6 cas unitaires de coordonnées non finies** : NaN et infinis positifs/négatifs sur chaque axe, rejet avant tout appel aux dépôts ou à la recherche.
- **4 cas unitaires de header Authorization absent/vide ou schéma incompatible** : null, chaîne vide, Basic, `bearer` en minuscules.

La suite existante conserve les vérifications de signature JWT incorrecte, token expiré, JWT patient valide, absence de JWT, pharmacies APPROVED, exclusion PENDING, distances en mètres, tri et limite des résultats.

### Frontend

- **4 cas supplémentaires** : nom de 201 caractères, quantité zéro, négative ou infinie, sans requête HTTP.
- **7 cas supplémentaires** : coordonnées hors limites sur chaque axe et coordonnées non finies, sans requête HTTP.
- **1 test via DOM Angular** : saisie réelle dans les contrôles par événements `input`, liaison `ngModel`, soumission du formulaire, coordonnées valides 0/0, nom de 200 caractères, quantité 3, bouton désactivé pendant l'envoi puis réactivé et message de succès affiché après HTTP 201 simulé.
- Les **deux tests d'erreur existants 401/500 ont été renforcés**, sans augmenter leur nombre : ils effectuent maintenant une nouvelle soumission réussie après l'erreur et vérifient le succès. Ils ne se contentent plus d'affirmer que l'état loading est désactivé.

Les tests existants d'ajout/suppression de lignes, GPS simulé, limites du rayon, POST multi-médicaments avec Bearer, anti-double soumission et route paresseuse ont été conservés.

## Anomalies reproduites et corrections

La première suite backend étendue a réellement exécuté **51 tests : 48 réussis, 2 échecs d'assertion, 1 erreur d'exécution, 0 ignoré**. Les dix nouveaux cas unitaires ajoutés ensuite ne faisaient pas partie de cette exécution.

| Anomalie révélée | Résultat avant correction | Correction ciblée | Vérification finale |
| --- | --- | --- | --- |
| Rayon décimal `100.5` | HTTP 201 au lieu de 400, troncature silencieuse | Désactivation de `accept-float-as-int` pour la désérialisation Jackson du service | Cas HTTP 400, aucune écriture |
| Quantité décimale `1.5` | HTTP 201 au lieu de 400, troncature silencieuse | Même option Jackson | Cas HTTP 400, aucune écriture |
| JWT PATIENT signé sans `sub` | NullPointerException depuis `UUID.fromString`, propagée par MockMvc | Vérification du sujet null/blanc et SecurityException déjà gérée en 401 par le contrôleur | Cas HTTP 401, aucune écriture |

La propriété `spring.jackson.deserialization.accept-float-as-int=false` est présente dans les configurations principale **et de test**, cette dernière remplaçant le fichier principal sur le classpath des tests. Elle s'applique aux champs entiers désérialisés par Jackson dans pharmacy-service, pas seulement à l'endpoint US09. Les autres tests pharmacy passent après ce changement. Aucune modification de schéma, dépendance Maven, algorithme métier, contrat de statut ou configuration Docker partagée n'a été nécessaire.

## Commandes réellement exécutées et preuves

Les preuves backend et de préservation sont sous `pharmacy-service/target/devops86-evidence/`, ignoré par Git.

1. `git status --short`, `git branch --show-current`, lecture du rapport DEVOPS-85 et des sources/tests ; `rg --files -g AGENTS.md -g '!node_modules' -g '!target'`.
2. `docker ps -a --format ...`, `docker volume ls -q`, puis lectures SQL avec `docker exec medidispo-qa-pharmacy-db psql -X -U qa_user ...` pour relever les empreintes de pharmacies et les historiques Flyway.
3. Suite Maven de référence, suite étendue en échec puis suite corrigée complète, avec trois noms distincts : `medidispo-devops86-baseline-20261101`, `medidispo-devops86-expanded-20261101`, `medidispo-devops86-final-20261101`.
4. Dans `frontend`, `npm.cmd test -- --watch=false` avant modification : 15/15 ; même commande après les ajouts : 27/27 ; `npm.cmd run build` après les ajouts : code 0, bundle initial 279,47 kB.
5. `docker logs` filtré sur versions/résumés, `docker inspect` pour vérifier la sortie finale 0, et `docker cp ...:/tmp/build/target/surefire-reports ...` pour conserver les rapports XML et texte.
6. Comparaisons PowerShell `Compare-Object` des relevés SQL et volumes, contrôle des noms des conteneurs préexistants, puis `git diff --check`.

Commande exacte de la suite finale, lancée depuis la racine du dépôt :

```powershell
docker run -d --name medidispo-devops86-final-20261101 `
  --mount 'type=bind,source=D:\oussama\mes-projet\secdevops\pharmacy-service,target=/source,readonly' `
  --mount 'type=volume,source=medidispo-qa-maven-cache,target=/root/.m2' `
  --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /tmp/build `
  maven:3.9-eclipse-temurin-21 sh -c 'cp -r /source/pom.xml /source/src . && java -version && mvn -B verify'
```

Les deux exécutions précédentes utilisent les mêmes montages et `mvn -B verify`, sans le `java -version` explicite. Le conteneur final confirme **Temurin Java 21.0.12.1**, puis **61 tests, 0 failure, 0 error, 0 skipped**, `BUILD SUCCESS`, durée Maven 26,781 s et code de sortie 0. Maven `verify` exécute les tests `*IntegrationTest` via Surefire dans la configuration actuelle ; aucune sélection de tests ni exclusion n'est appliquée.

Les sources sont montées en lecture seule et copiées dans `/tmp/build`. L'accès au socket Docker pour Testcontainers reprend l'autorisation explicite donnée pendant DEVOPS-85. Les trois classes d'intégration utilisent `@Container`, `@ServiceConnection` et `postgis/postgis:16-3.5`, donc des bases temporaires propres, créées et arrêtées par Testcontainers. Aucun accès au fichier `.env` réel n'est nécessaire pour ces tests ; la clé JWT du classpath de test est une fixture publique de test.

Preuves conservées : `baseline-surefire/`, `expanded-failing-surefire/`, `final-surefire/`, `maven-final-summary.txt`, `git-before.txt`, `docker-before.txt`, `volumes-before.txt`, `pharmacy_qa-before.txt`, `pharmacy_qa-after.txt`, `pharmacy_devops85-before.txt`, `pharmacy_devops85-after.txt`. Les résumés ne contiennent aucun secret réel.

## Préservation des bases et du travail existant

- **Aucun conteneur préexistant manquant** après les tests ; aucun stop, restart ou rm de conteneur préexistant effectué.
- **Liste des volumes persistants identique** avant/après. Aucun prune ni suppression de volume exécuté.
- Dans `pharmacy_qa` et `pharmacy_devops85`, l'empreinte MD5 calculée à partir des lignes complètes de `pharmacies`, triées, et les historiques Flyway sont identiques avant/après. Ce contrôle porte sur ces données et historiques ; il n'est pas présenté comme un hash de toutes les bases PostgreSQL du poste.
- `pharmacy_devops85` contient toujours **2 demandes, 4 médicaments, 2 associations**, conformément au relevé DEVOPS-85.
- Les bases arrêtées n'ont pas été démarrées ni interrogées ; aucun accès en écriture à une base existante n'a été effectué. Les écritures et suppressions de fixtures des tests sont limitées aux conteneurs éphémères Testcontainers.
- Les services locaux DEVOPS-85 n'ont pas été redéployés : le JAR corrigé est issu du build de test final, sans remplacement du JAR utilisé par le backend déjà actif. Les corrections sont validées dans le contexte Spring Boot des tests d'intégration.
- Aucun commit, push, merge, rebase, reset ou nettoyage Git. Aucun fichier de coéquipier supprimé.

## Fichiers modifiés pendant DEVOPS-86

Cette liste distingue les changements de cette mission des nombreux changements déjà présents au démarrage.

| Fichier | Modification DEVOPS-86 |
| --- | --- |
| `pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/availability/AvailabilityJwtServiceTest.java` | 4 cas de headers Authorization |
| `pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestControllerTest.java` | 6 cas de coordonnées non finies sans appels aux dépôts |
| `pharmacy-service/src/test/java/ma/medidispo/pharmacy_service/availability/AvailabilityRequestIntegrationTest.java` | 29 nouveaux cas HTTP/PostGIS/transactionnels ; tests existants conservés |
| `frontend/src/app/pages/availability-request.spec.ts` | 12 nouveaux cas et renforcement des deux scénarios de réessai |
| `pharmacy-service/src/main/java/ma/medidispo/pharmacy_service/availability/AvailabilityJwtService.java` | Rejet explicite d'un sujet JWT absent/blanc |
| `pharmacy-service/src/main/resources/application.properties` | Refus de la troncature JSON décimale vers entier |
| `pharmacy-service/src/test/resources/application.properties` | Même configuration Jackson pour les tests |
| `docs/RAPPORT_DEVOPS_86_CODEX.md` | Nouveau rapport |

Les preuves locales ont été ajoutées uniquement sous `target/devops86-evidence/`. Les builds Angular produisent leurs sorties habituelles ignorées. Aucun secret réel, cache npm, `.env` ni `.bak` n'a été ajouté à l'index Git.

## Scénarios non couverts, blocages et DEVOPS-87

**Aucun test demandé pour DEVOPS-86 n'a été impossible à exécuter.** Les tests unitaires, d'intégration PostgreSQL/PostGIS, Angular et le build de production ont tous été exécutés. Les trois échecs intermédiaires ont été corrigés puis retestés.

Les limites restantes sont explicites : pas de login réel auprès d'auth-service, pas de GPS physique, pas de scénario navigateur complet avec proxy et soumission dans cette mission, pas de charge/concurrence, ni de politique de diffusion à plus de 100 pharmacies. Les coordonnées exactement sur la frontière géographique ne constituent pas une nouvelle assertion de ce ticket : les tests ajoutés vérifient rayon moins un et plus un mètre, pour éviter une comparaison dépendante des arrondis géodésiques. Aucune couverture de lignes ou de branches chiffrée n'est revendiquée : aucun outil de mesure de couverture n'a été lancé.

Pour préparer DEVOPS-87, confirmer son libellé et ses critères officiels, puis prévoir une validation de bout en bout dans un environnement isolé : véritable patient inscrit/connecté, clé partagée volontairement avec pharmacy-service, lancement du JAR corrigé, route Angular avec proxy, GPS contrôlé dans le navigateur et soumission, assertions SQL sur médicaments/pharmacies/PENDING, ainsi que rejets JWT et absence d'écritures invalides. Conserver cette suite de 88 tests comme base de non-régression.
