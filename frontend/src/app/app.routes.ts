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
    // US15 (amélioration) : le pharmacien déclare ses gardes
    path: 'pharmacien/gardes',
    title: 'Mes gardes – DwaDispo',
    loadComponent: () => import('./pages/pharmacist-duties').then((m) => m.PharmacistDutiesPage),
  },
  {
    path: 'mes-ordonnances',
    title: 'Mes ordonnances – DwaDispo',
    loadComponent: () => import('./pages/prescription-history').then(m => m.PrescriptionHistoryPage),
  },
  {
    // US20 : mon profil (consulter, modifier, changer le mot de passe)
    path: 'profil',
    title: 'Mon profil – DwaDispo',
    loadComponent: () => import('./pages/profile').then((m) => m.ProfilePage),
  },
  {
    // US22 : alertes de retour en stock (patient)
    path: 'alertes',
    title: 'Mes alertes – DwaDispo',
    loadComponent: () => import('./pages/stock-alerts').then((m) => m.StockAlertsPage),
  },
  {
    // US22 : le pharmacien met à jour son stock
    path: 'pharmacien/stock',
    title: 'Mon stock – DwaDispo',
    loadComponent: () => import('./pages/pharmacist-stock').then((m) => m.PharmacistStockPage),
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
