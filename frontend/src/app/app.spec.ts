import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    sessionStorage.clear();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the route outlet', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('router-outlet')).not.toBeNull();
  });

  it('affiche uniquement les liens publics si personne n’est connecté', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(nav.textContent).toContain('Accueil');
    expect(nav.textContent).toContain('Connexion');
    expect(nav.textContent).toContain('Créer un compte');
    expect(nav.textContent).not.toContain('Médicaments');
    expect(nav.textContent).not.toContain('Scanner');
    expect(nav.querySelector('button')).toBeNull();
  });

  it('propose la déconnexion quand un patient est connecté', async () => {
    sessionStorage.setItem('access_token', 'jwt');

    const fixture = TestBed.createComponent(App);
    http.expectOne('/api/auth/me').flush({
      id: '1',
      email: 'patient@test.ma',
      firstName: 'Patient',
      lastName: 'Test',
      role: 'PATIENT',
      createdAt: '2026-01-01T00:00:00',
    });

    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const nav = element.querySelector('nav')!;
    expect(nav.textContent).toContain('Déconnexion');
    expect(nav.textContent).toContain('Scanner');
    expect(nav.textContent).toContain('Médicaments');
    expect(nav.querySelector('app-notification-bell')).not.toBeNull();
  });
});
