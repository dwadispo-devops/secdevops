# Frontend

US17 : correction des quantités et estimation indicative sur `/verification`. Voir le [guide de test patient et les commandes PowerShell](../docs/US17_ESTIMATION_ORDONNANCE.md).

Le parcours patient de réservation avec QR code valable deux heures est décrit dans le [guide US12](../docs/US12_RESERVATION_QR.md), avec les commandes de test et la recette patient/pharmacien.

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.2.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## Navigation et authentification

### Pages publiques
- `/` : page d'accueil et présentation de DwaDispo.
- `/connexion` : connexion patient.
- `/inscription` : création d'un compte patient.
- `/pharmacien/connexion` : connexion pharmacien.
- `/pharmacien/inscription` : inscription pharmacien.

Le menu public affiche uniquement Accueil, Connexion et Créer un compte.

### Accès selon le rôle
- Patient (`PATIENT`) : accès aux fonctionnalités patient (scanner, ordonnances, pharmacies, médicaments, demandes et suivi).
- Pharmacien (`PHARMACIEN`) : accès aux demandes reçues et à la gestion des gardes.
- Les deux rôles peuvent accéder à leur profil.

Les routes privées utilisent `auth.guard.ts` :
- `authGuard` vérifie qu'un utilisateur est connecté.
- `roleGuard` vérifie également que son rôle est autorisé.
- Sans session valide, l'utilisateur est redirigé vers `/connexion`.

Après connexion, le frontend récupère le profil avec `GET /api/auth/me`.
Le token est conservé dans `sessionStorage`.

Important : les guards Angular contrôlent la navigation côté frontend.
Les autorisations réelles doivent également être vérifiées par le backend.

### Lancer et vérifier le frontend

Depuis le dossier frontend :

Installation :

    npm install

Démarrage :

    npm start

Compilation :

    npm run build

Tous les tests :

    npm test -- --watch=false

Tests de disponibilité :

    npx ng test --watch=false --include="src/app/pages/availability-request.spec.ts"
