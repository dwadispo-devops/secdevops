import { Routes } from '@angular/router';

const enConstruction = 'Cette page sera développée dans un prochain sprint.';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Accueil – MédiDispo',
    loadComponent: () => import('./pages/home').then((m) => m.HomePage),
  },
  {
    path: 'scan',
    title: 'Scanner une ordonnance – MédiDispo',
    data: { titre: 'Scanner une ordonnance', texte: enConstruction },
    loadComponent: () => import('./pages/placeholder').then((m) => m.PlaceholderPage),
  },
  {
    path: 'medicaments',
    title: 'Médicaments – MédiDispo',
    data: { titre: 'Médicaments', texte: enConstruction },
    loadComponent: () => import('./pages/placeholder').then((m) => m.PlaceholderPage),
  },
  {
    path: 'pharmacies',
    title: 'Pharmacies – MédiDispo',
    data: { titre: 'Pharmacies', texte: enConstruction },
    loadComponent: () => import('./pages/placeholder').then((m) => m.PlaceholderPage),
  },
  {
    path: 'connexion',
    title: 'Connexion – MédiDispo',
    data: { titre: 'Connexion', texte: enConstruction },
    loadComponent: () => import('./pages/placeholder').then((m) => m.PlaceholderPage),
  },
    {
    path: 'verification',
    title: 'Vérifier mon ordonnance – MédiDispo',
    loadComponent: () => import('./pages/verification').then((m) => m.VerificationPage),
  },
  {
    path: 'demande',
    title: 'Demande de disponibilité – MédiDispo',
    data: { titre: 'Demande de disponibilité', texte: enConstruction },
    loadComponent: () => import('./pages/placeholder').then((m) => m.PlaceholderPage),
  },
  { path: '**', redirectTo: '' },
];
