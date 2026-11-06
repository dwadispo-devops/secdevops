import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LoginPage, safeReturnUrl } from './login';

describe('LoginPage (US02)', () => {
  let http: HttpTestingController;
  let page: LoginPage;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', component: LoginPage }])],
    });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(LoginPage).componentInstance;
  });

  afterEach(() => http.verify());

  it('stocke les jetons après une connexion réussie', () => {
    page.email = ' patient@test.ma '; page.password = 'Patient123';
    page.login();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'patient@test.ma', password: 'Patient123' });
    req.flush({ accessToken: 'jwt-acces', refreshToken: 'jwt-refresh' });
    expect(sessionStorage.getItem('access_token')).toBe('jwt-acces');
    expect(sessionStorage.getItem('refresh_token')).toBe('jwt-refresh');
    expect(page.password).toBe('');
  });

  it('affiche un message clair si les identifiants sont faux', () => {
    page.email = 'patient@test.ma'; page.password = 'mauvais1';
    page.login();
    http.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(page.error()).toBe('Identifiants incorrects.');
    expect(sessionStorage.getItem('access_token')).toBeNull();
  });

  it('refuse les redirections vers un site externe', () => {
    expect(safeReturnUrl('/demande-disponibilite')).toBe('/demande-disponibilite');
    expect(safeReturnUrl('//site-pirate.com')).toBe('/scan');
    expect(safeReturnUrl('https://site-pirate.com')).toBe('/scan');
    expect(safeReturnUrl(null)).toBe('/scan');
  });
});
