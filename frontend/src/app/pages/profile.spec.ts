import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { ProfilePage } from './profile';

describe('ProfilePage (US20 – mon profil)', () => {
  let http: HttpTestingController;
  const profil = {
    id: 'u1', email: 'amina@test.ma', firstName: 'Amina', lastName: 'Test',
    role: 'PATIENT', createdAt: '2026-11-01T10:00:00Z',
  };

  async function start() {
    TestBed.configureTestingModule({
      imports: [ProfilePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    http.expectOne('/api/auth/me').flush(profil);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('affiche les informations du compte', async () => {
    const fixture = await start();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('amina@test.ma');
    expect(text).toContain('Patient');
  });

  it('enregistre le nouveau prénom et le nouveau nom', async () => {
    const page = (await start()).componentInstance;
    page.firstName = 'Salma';
    page.lastName = 'Bennani';
    page.save();
    const req = http.expectOne('/api/auth/me');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ firstName: 'Salma', lastName: 'Bennani' });
    req.flush({ ...profil, firstName: 'Salma', lastName: 'Bennani' });
    expect(page.success()).toContain('mis à jour');
  });

  it('refuse deux nouveaux mots de passe différents sans appeler le serveur', async () => {
    const page = (await start()).componentInstance;
    page.currentPassword = 'Secret123';
    page.newPassword = 'Nouveau456';
    page.confirmPassword = 'Autre789';
    page.changePassword();
    expect(page.error()).toContain('ne correspondent pas');
  });

  it('après un changement de mot de passe, efface la session et renvoie vers la connexion', async () => {
    const page = (await start()).componentInstance;
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    sessionStorage.setItem('access_token', 'a');
    sessionStorage.setItem('refresh_token', 'r');
    page.currentPassword = 'Secret123';
    page.newPassword = 'Nouveau456';
    page.confirmPassword = 'Nouveau456';
    page.changePassword();
    http.expectOne('/api/auth/me/password').flush(null, { status: 204, statusText: 'No Content' });
    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { motDePasse: 'modifie' } });
  });

  it('affiche le message du serveur si le mot de passe actuel est faux', async () => {
    const page = (await start()).componentInstance;
    page.currentPassword = 'Faux0000';
    page.newPassword = 'Nouveau456';
    page.confirmPassword = 'Nouveau456';
    page.changePassword();
    http.expectOne('/api/auth/me/password').flush(
      { detail: 'Le mot de passe actuel est incorrect.' }, { status: 400, statusText: 'Bad Request' });
    expect(page.error()).toBe('Le mot de passe actuel est incorrect.');
  });
  it('refuse la suppression sans la case cochée, sans appeler le serveur', async () => {
    const page = (await start()).componentInstance;
    page.deletePassword = 'Secret123';
    page.confirmDelete = false;
    page.deleteAccount();
    expect(page.error()).toContain('Cochez la case');
  });

  it('supprime le compte, efface la session et renvoie vers l’accueil', async () => {
    const page = (await start()).componentInstance;
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    sessionStorage.setItem('access_token', 'a');
    sessionStorage.setItem('last_prescription_id', 'p1');
    page.deletePassword = 'Secret123';
    page.confirmDelete = true;
    page.deleteAccount();
    const req = http.expectOne('/api/auth/me/suppression');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ password: 'Secret123' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(sessionStorage.getItem('last_prescription_id')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/'], { queryParams: { compte: 'supprime' } });
  });

  it('affiche le message du serveur si le mot de passe de suppression est faux', async () => {
    const page = (await start()).componentInstance;
    page.deletePassword = 'Faux0000';
    page.confirmDelete = true;
    page.deleteAccount();
    http.expectOne('/api/auth/me/suppression').flush(
      { detail: 'Le mot de passe est incorrect.' }, { status: 400, statusText: 'Bad Request' });
    expect(page.error()).toBe('Le mot de passe est incorrect.');
  });
});
