import { Routes } from '@angular/router';

const enConstruction = 'Cette page sera développée dans un prochain sprint.';

export const routes: Routes = [
  {
    path: 'pharmacien/connexion',
    title: 'Connexion pharmacien - MédiDispo',
    loadComponent: () => import('./pages/pharmacist-login').then(m => m.PharmacistLoginPage),
  },
  {
    path: 'pharmacien/inscription',
    title: 'Inscrire ma pharmacie – DwaDispo',
    loadComponent: () => import('./pages/pharmacist-register').then((m) => m.PharmacistRegisterPage),
  },
  {
    path: 'pharmacien/demandes',
    title: 'Demandes reçues - MédiDispo',
    loadComponent: () => import('./pages/pharmacist-requests').then(m => m.PharmacistRequestsPage),
  },
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
    loadComponent: () => import('./pages/medications').then((m) => m.MedicationsPage),
  },
  {
    path: 'pharmacies',
    title: 'Pharmacies – DwaDispo',
    loadComponent: () => import('./pages/pharmacies').then((m) => m.PharmaciesPage),
  },
  {
    // US15 : pharmacies de garde du jour
    path: 'garde',
    title: 'Pharmacies de garde – DwaDispo',
    loadComponent: () => import('./pages/pharmacies-garde').then((m) => m.PharmaciesGardePage),
  },
  {
    path: 'connexion',
    title: 'Connexion – DwaDispo',
    loadComponent: () => import('./pages/login').then((m) => m.LoginPage),
  },
  {
    path: 'inscription',
    title: 'Créer un compte – DwaDispo',
    loadComponent: () => import('./pages/register').then((m) => m.RegisterPage),
  },
    {
    path: 'verification',
    title: 'Vérifier mon ordonnance – MédiDispo',
    loadComponent: () => import('./pages/verification').then((m) => m.VerificationPage),
  },
  {
    path: 'demande',
    redirectTo: 'demande-disponibilite',
  },
  {
    path: 'suivi-demande/:id',
    title: 'Suivi de ma demande – DwaDispo',
    loadComponent: () => import('./pages/request-tracking').then((m) => m.RequestTrackingPage),
  },
  {
    path: 'suivi-demande',
    title: 'Suivi de ma demande – DwaDispo',
    loadComponent: () => import('./pages/request-tracking').then((m) => m.RequestTrackingPage),
  },
  { path: '**', redirectTo: '' },
];
