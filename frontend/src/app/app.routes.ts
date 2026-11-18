import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './services/auth.guard';

export const routes: Routes = [
  // Pages publiques
  {
    path: '',
    pathMatch: 'full',
    title: 'Accueil – DwaDispo',
    loadComponent: () => import('./pages/home').then(m => m.HomePage),
  },
  {
    path: 'connexion',
    title: 'Connexion – DwaDispo',
    loadComponent: () => import('./pages/login').then(m => m.LoginPage),
  },
  {
    path: 'inscription',
    title: 'Créer un compte – DwaDispo',
    loadComponent: () => import('./pages/register').then(m => m.RegisterPage),
  },
  {
    path: 'pharmacien/connexion',
    title: 'Connexion pharmacien - MédiDispo',
    loadComponent: () => import('./pages/pharmacist-login').then(m => m.PharmacistLoginPage),
  },
  {
    path: 'pharmacien/inscription',
    title: 'Inscrire ma pharmacie – DwaDispo',
    loadComponent: () => import('./pages/pharmacist-register').then(m => m.PharmacistRegisterPage),
  },

  // Pages réservées aux patients
  {
    path: 'scan',
    canActivate: [roleGuard('PATIENT')],
    title: 'Scanner une ordonnance – DwaDispo',
    loadComponent: () => import('./pages/scan').then(m => m.ScanPage),
  },
  {
    path: 'verification',
    canActivate: [roleGuard('PATIENT')],
    title: 'Vérifier mon ordonnance – MédiDispo',
    loadComponent: () => import('./pages/verification').then(m => m.VerificationPage),
  },
  {
    path: 'medicaments',
    canActivate: [roleGuard('PATIENT')],
    title: 'Médicaments – DwaDispo',
    loadComponent: () => import('./pages/medications').then(m => m.MedicationsPage),
  },
  {
    path: 'pharmacies',
    canActivate: [roleGuard('PATIENT')],
    title: 'Pharmacies – DwaDispo',
    loadComponent: () => import('./pages/pharmacies').then(m => m.PharmaciesPage),
  },
  {
    path: 'garde',
    canActivate: [roleGuard('PATIENT')],
    title: 'Pharmacies de garde – DwaDispo',
    loadComponent: () => import('./pages/pharmacies-garde').then(m => m.PharmaciesGardePage),
  },
  {
    path: 'demande-disponibilite',
    canActivate: [roleGuard('PATIENT')],
    title: 'Demande de disponibilité - MédiDispo',
    loadComponent: () => import('./pages/availability-request').then(m => m.AvailabilityRequestPage),
  },
  {
    path: 'demande',
    redirectTo: 'demande-disponibilite',
  },
  {
    path: 'suivi-demande/:id',
    canActivate: [roleGuard('PATIENT')],
    title: 'Suivi de ma demande – DwaDispo',
    loadComponent: () => import('./pages/request-tracking').then(m => m.RequestTrackingPage),
  },
  {
    path: 'suivi-demande',
    canActivate: [roleGuard('PATIENT')],
    title: 'Suivi de ma demande – DwaDispo',
    loadComponent: () => import('./pages/request-tracking').then(m => m.RequestTrackingPage),
  },
  {
    path: 'mes-ordonnances',
    canActivate: [roleGuard('PATIENT')],
    title: 'Mes ordonnances – DwaDispo',
    loadComponent: () => import('./pages/prescription-history').then(m => m.PrescriptionHistoryPage),
  },
  {
    path: 'alertes',
    canActivate: [roleGuard('PATIENT')],
    title: 'Mes alertes – DwaDispo',
    loadComponent: () => import('./pages/stock-alerts').then(m => m.StockAlertsPage),
  },

  // Pages réservées aux pharmaciens
  {
    path: 'pharmacien/demandes',
    canActivate: [roleGuard('PHARMACIEN')],
    title: 'Demandes reçues - MédiDispo',
    loadComponent: () => import('./pages/pharmacist-requests').then(m => m.PharmacistRequestsPage),
  },
  {
    path: 'pharmacien/gardes',
    canActivate: [roleGuard('PHARMACIEN')],
    title: 'Mes gardes – DwaDispo',
    loadComponent: () => import('./pages/pharmacist-duties').then(m => m.PharmacistDutiesPage),
  },
  {
    path: 'pharmacien/stock',
    canActivate: [roleGuard('PHARMACIEN')],
    title: 'Mon stock – DwaDispo',
    loadComponent: () => import('./pages/pharmacist-stock').then(m => m.PharmacistStockPage),
  },

  // Accessible à tout utilisateur connecté
  {
    path: 'profil',
    canActivate: [authGuard],
    title: 'Mon profil – DwaDispo',
    loadComponent: () => import('./pages/profile').then(m => m.ProfilePage),
  },

  { path: '**', redirectTo: '' },
];
