import { Routes } from '@angular/router';

const enConstruction = 'Cette page sera développée dans un prochain sprint.';

export const routes: Routes = [
  {
    path: 'demande-disponibilite',
    title: 'Demande de disponibilité - MédiDispo',
    loadComponent: () => import('./pages/availability-request').then((m) => m.AvailabilityRequestPage),
  },
  {
    path: '',
    pathMatch: 'full',
    title: 'Accueil – DwaDispo',
    loadComponent: () => import('./pages/home').then((m) => m.HomePage),
  },
  {
    // US06 : photographier ou importer son ordonnance
    path: 'scan',
    title: 'Scanner une ordonnance – DwaDispo',
    loadComponent: () => import('./pages/scan').then((m) => m.ScanPage),
  },
  {
    path: 'medicaments',
    title: 'Médicaments – DwaDispo',
    data: { titre: 'Médicaments', texte: enConstruction },
    loadComponent: () => import('./pages/placeholder').then((m) => m.PlaceholderPage),
  },
  {
    path: 'pharmacies',
    title: 'Pharmacies – DwaDispo',
    loadComponent: () => import('./pages/pharmacies').then((m) => m.PharmaciesPage),
  },
  {
    path: 'connexion',
    title: 'Connexion – DwaDispo',
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
