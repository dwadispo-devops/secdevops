# US12 — Réserver et obtenir un QR code valable deux heures

## Comportement livré

Le patient réserve depuis `/suivi-demande/{id}`, après la réponse « Disponible » d'une pharmacie. Le backend crée une réservation valable **exactement 7 200 secondes (120 minutes)** à partir de sa date de création en base. Les contrôles existants restent en place : propriétaire de la demande, rôle PATIENT, disponibilité confirmée, une réservation active par demande, trois au maximum par patient.

Le suivi affiche le QR, le code texte, la pharmacie, le prix et l'échéance complète (date, heures, minutes, secondes), dans le fuseau **Africa/Casablanca**, indiqué comme heure du Maroc. Cette conversion utilise `Intl.DateTimeFormat` ; elle ne dépend pas du fuseau choisi sur l'ordinateur.

Le QR est un SVG généré localement avec `qrcode`. Il encode **uniquement les six caractères du code de retrait** : aucun nom, identifiant patient, médicament, prix, URL ou jeton de connexion. La matrice noire est entourée de quatre modules blancs. Aucun service externe ne reçoit le code. Le texte reste utilisable si la génération du QR échoue.

Le QR et son code texte ne sont plus présentés comme utilisables si la réservation est expirée, retirée ou annulée. Une horloge locale vérifiée chaque seconde masque le QR à l'échéance, même sans nouvelle réponse serveur. Le statut serveur reste prioritaire : il invalide notamment un retrait confirmé, récupéré par le suivi toutes les dix secondes.

**L'autorité sur la validité reste le backend**, qui refuse un retrait dès `expires_at <= now()`, même si le traitement automatique d'expiration n'est pas encore passé. Celui-ci met à jour le statut en base et crée la notification, par défaut toutes les 60 secondes. Une capture d'écran peut toujours exister après expiration, mais le serveur refuse son code.

La consultation distingue une absence de réservation (404), une session expirée (401), un accès interdit (403) et une panne réseau/serveur. Lorsqu'il est impossible de vérifier la réservation, un message apparaît, le QR est masqué et une nouvelle création est bloquée jusqu'à récupération. Une nouvelle tentative est effectuée automatiquement. Une consultation ancienne terminée après une création ne peut plus effacer sa confirmation.

Les réservations enregistrées avant cette version gardent leur ancienne échéance. Les **nouvelles** réservations ont la durée de deux heures. Aucune migration SQL n'est nécessaire et la migration V7 reste inchangée.

## Tests automatisés

Pré requis : Node/npm compatibles avec le projet, un **JDK complet** capable de compiler Java 21, Docker Desktop démarré en mode Linux. Une simple JRE ne suffit pas pour Maven. Les tests backend démarrent leurs propres bases PostGIS temporaires via Testcontainers et n'utilisent pas les bases applicatives.

Depuis `pharmacy-service`, dans PowerShell :

```powershell
.\mvnw.cmd '-Dtest=ReservationIntegrationTest,ReservationRetraitIntegrationTest,PickupCodeGeneratorTest,NotificationIntegrationTest' test
```

Si nécessaire, définir `JAVA_HOME` dans ce terminal vers le répertoire d'un JDK installé avant cette commande.

Depuis `frontend` :

```powershell
npm ci
npm test -- --watch=false
npm run build
```

Pour ne lancer que les tests US12 Angular :

```powershell
npm test -- --watch=false --include=src/app/pages/request-tracking.spec.ts --include=src/app/components/reservation-qr.spec.ts
```

Les assertions backend vérifient la durée **sans arrondi**, une réservation encore utilisable après 90 minutes, la validité juste avant l'échéance et le refus à l'échéance exacte, l'expiration et la nouvelle réservation, les notifications et les droits. Les 21 tests de retrait US13 existants sont exécutés sans modifier leur fichier.

Les tests frontend reconstruisent les pixels du SVG rendu puis les décodent avec **jsQR**, une bibliothèque indépendante du générateur. Ils vérifient que le résultat est exactement le code texte. Ils couvrent également création, restauration, échéance locale, statuts terminaux, erreurs, récupération et requêtes simultanées de consultation/création.

## Démarrage pour les tests manuels

Depuis la racine du projet, avec le `.env` local déjà configuré selon les README des services :

```powershell
docker compose up -d --build
docker compose ps
```

Cela démarre notamment Eureka (`8761`), Gateway (`8080`), auth (`8081`), pharmacy (`8082`), medication (`8083`), prescription (`8085`) et leurs bases. Attendre que les services soient prêts et enregistrés dans Eureka. Le parcours avec saisie manuelle des médicaments ne nécessite pas de lecture IA d'ordonnance.

Dans un deuxième terminal :

```powershell
Set-Location frontend
npm ci
npm start
```

Ouvrir `http://localhost:4200`. Le proxy Angular transmet `/api/**` à `http://localhost:8080`. Si les services sont déjà lancés autrement, utiliser cette même passerelle et veiller à redémarrer `pharmacy-service` avec le nouveau code.

## Recette avec deux comptes

Utiliser deux profils de navigateur séparés, ou une fenêtre normale pour le patient et une fenêtre privée pour le pharmacien. Les jetons sont enregistrés dans `sessionStorage` : ne pas alterner les deux comptes dans le même onglet.

### 1. Préparer le pharmacien

1. Ouvrir `/pharmacien/connexion` avec un compte pharmacien propriétaire d'une pharmacie.
2. S'il n'existe pas encore, passer par `/pharmacien/inscription` pour créer le compte **et sa pharmacie** ; utiliser un numéro d'autorisation unique et des coordonnées proches de la position utilisée par le patient.
3. Ouvrir `/pharmacien/demandes`. Garder cette fenêtre ouverte.

### 2. Envoyer une demande patient

1. Ouvrir `/connexion`, ou créer un compte patient sur `/inscription`.
2. Aller directement sur `/demande-disponibilite` pour saisir manuellement un médicament et sa quantité. L'autre parcours possible est `/scan`, vérification de l'ordonnance, puis demande.
3. Autoriser la géolocalisation et choisir un rayon incluant la pharmacie préparée. Vérifier qu'elle apparaît dans l'aperçu ; sinon corriger la position ou le rayon.
4. Envoyer la demande. Conserver son URL `/suivi-demande/{id}`.

### 3. Répondre comme pharmacien

1. Dans l'autre profil, actualiser `/pharmacien/demandes`.
2. Sélectionner la demande correspondant à son identifiant.
3. Choisir « Disponible », saisir un prix, par exemple `85.50`, puis envoyer la réponse.

### 4. Réserver et contrôler le QR

1. Côté patient, attendre au maximum un cycle d'actualisation de dix secondes.
2. Cliquer sur « Réserver ici » pour cette pharmacie.
3. Vérifier le QR, le code texte de six caractères, le nom de la pharmacie, le prix et la date/heure d'expiration au Maroc.
4. Dans F12 → Réseau/Network, ouvrir la réponse du `POST .../reservations` (HTTP 201). Noter `id`, `pharmacyId`, `code`, `createdAt`, `expiresAt`.
5. Contrôler les dates avec cette expression dans la console, en remplaçant les deux chaînes par les valeurs JSON :

```javascript
(Date.parse('EXPIRES_AT') - Date.parse('CREATED_AT')) / 60000
// Résultat attendu : 120.
```

6. Lire le QR avec un lecteur capable d'afficher le texte brut. Résultat attendu : exactement le code texte, sans URL ni données personnelles. Une caméra de téléphone ne proposant que l'ouverture de liens peut ne pas afficher de résultat pour ce QR texte.
7. Recharger la page : la même réservation et le même QR doivent revenir.
8. Les boutons de réservation sont désactivés tant qu'une réservation est active. Avec trois demandes distinctes déjà réservées, une quatrième réservation doit afficher le refus du serveur.

### 5. Tester expiration et erreurs

**Expiration réelle :** conserver une réservation sans retrait pendant deux heures. Après l'échéance, le QR disparaît et « Réservation expirée » apparaît. La réservation devient de nouveau possible chez une pharmacie disponible.

**Expiration accélérée, uniquement dans une base locale de test :** ouvrir une session SQL, par exemple `docker compose exec pharmacy-db psql -U VOTRE_DB_USER -d pharmacydb`, puis appliquer à la seule réservation de test, en remplaçant l'UUID :

```sql
SELECT id, status, created_at, expires_at,
       extract(epoch FROM expires_at - created_at) / 60 AS minutes
FROM reservations WHERE id = 'UUID_RESERVATION';

UPDATE reservations
SET expires_at = now() + interval '20 seconds'
WHERE id = 'UUID_RESERVATION' AND status = 'ACTIVE';
```

Recharger le suivi pour récupérer cette échéance raccourcie, puis attendre vingt secondes. Le QR doit disparaître, même si le prochain appel réseau n'a pas encore eu lieu. Cette manipulation ne vérifie pas les 120 minutes d'origine : elles sont contrôlées séparément à l'étape précédente et dans les tests automatisés. La notification d'expiration doit ensuite parler des **deux heures** et n'être créée qu'une fois. Les e-mails de test sont consultables dans Mailpit sur `http://localhost:8025` lorsque l'envoi est activé.

**Panne réseau :** dans les outils du navigateur, choisir Network → Offline pendant au moins dix secondes. Un message doit apparaître, le QR ne doit plus être présenté comme vérifié. Repasser Online : le suivi récupère automatiquement son état. Pour observer spécifiquement l'horloge locale, après récupération d'une échéance proche, bloquer la réponse réseau de consultation jusqu'au-delà de cette échéance.

**Session absente :** dans le profil patient de test, exécuter `sessionStorage.removeItem('access_token')`, puis recharger. Les données et le QR doivent disparaître, avec un lien pour se reconnecter.

**Mauvais compte :** ouvrir l'URL de suivi avec un autre patient, puis avec le pharmacien. Aucun QR ne doit apparaître (404 pour la demande d'un autre patient, 403 pour le rôle pharmacien). Revenir au compte patient initial pour poursuivre.

### 6. Vérifier le statut retiré sans développer l'interface US13

Cette étape facultative utilise les endpoints US13 existants depuis la console du profil **pharmacien**, sur une réservation active distincte du test d'expiration. Remplacer les valeurs par celles de la réponse patient. Ne pas copier ni publier le jeton de connexion.

```javascript
const pharmacie = 'UUID_PHARMACIE';
const codeRetrait = 'CODE_6_CARACTERES';
const headersRetrait = {
  Authorization: `Bearer ${sessionStorage.getItem('access_token')}`
};
const recherche = await fetch(
  `/api/pharmacies/${pharmacie}/reservations/${codeRetrait}`,
  { headers: headersRetrait }
);
console.log(recherche.status); // 200 si propriétaire et réservation active ; 404 si expirée.
const reservationTrouvee = recherche.ok ? await recherche.json() : null;
```

Pour valider effectivement le retrait de cette réservation de test :

```javascript
if (reservationTrouvee) {
  const retrait = await fetch(
    `/api/pharmacies/${pharmacie}/reservations/${reservationTrouvee.id}/pickup`,
    { method: 'POST', headers: headersRetrait }
  );
  console.log(retrait.status, await retrait.json()); // 200 et statut RETIREE.
}
```

Le patient doit voir « Médicaments retirés » et aucun QR après le prochain rafraîchissement. Répéter la validation doit produire 409. Après expiration, recherche et retrait doivent être refusés avec 404, même avant le passage du traitement automatique. Les tests automatisés vérifient ces protections. Aucune nouvelle interface pharmacien n'est incluse dans US12.

## Limites et validation

- L'horloge locale sert à l'affichage (résolution d'une seconde, ralentissement possible d'un onglet en arrière-plan) ; l'horloge de la base fait foi pour le retrait.
- Un retrait effectué ailleurs est visible au prochain rafraîchissement réseau, normalement sous dix secondes.
- Une panne réseau masque le QR par précaution jusqu'à vérification. Elle ne prolonge jamais la validité.
- La confirmation de réservation utilise déjà l'échéance réelle dans les notifications. Les anciens messages enregistrés ne sont pas réécrits.
- Le build peut signaler le format CommonJS de `qrcode` et de Leaflet ainsi que le budget CSS de la page pharmacies ; ces avertissements n'empêchent pas le build.
- La recette manuelle avec les deux comptes doit être validée par l'utilisateur avant toute opération Git de livraison. Aucun commit, push, merge ou changement de branche ne fait partie de cette implémentation.

Références des bibliothèques : [qrcode](https://github.com/soldair/node-qrcode), [jsQR (tests uniquement)](https://github.com/cozmo/jsQR).
