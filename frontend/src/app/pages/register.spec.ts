import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RegisterPage, PASSWORD_RULE } from './register';
import { AuthService } from '../services/auth.service';

describe('RegisterPage (US01)', () => {
  let http: HttpTestingController;
  let page: RegisterPage;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', component: RegisterPage }])],
    });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(RegisterPage).componentInstance;
    Object.assign(page, { firstName: 'Amina', lastName: 'Test', email: 'amina@test.ma', password: 'Patient123', confirm: 'Patient123' });
  });

  afterEach(() => http.verify());

  it('applique la même règle de mot de passe que le back-end', () => {
    expect(PASSWORD_RULE.test('Patient123')).toBe(true);
    expect(PASSWORD_RULE.test('court1')).toBe(false);
    expect(PASSWORD_RULE.test('sanschiffre')).toBe(false);
    expect(PASSWORD_RULE.test('12345678')).toBe(false);
  });

  it('bloque l’envoi si la confirmation est différente', () => {
    page.confirm = 'Autre1234';
    expect(page.canSubmit()).toBe(false);
  });

  it('crée le compte puis connecte automatiquement le patient', () => {
    page.register();
    http.expectOne('/api/auth/register').flush({ id: '1', role: 'PATIENT' });
    http.expectOne('/api/auth/login').flush({ accessToken: 'jwt-acces' });
    expect(sessionStorage.getItem('access_token')).toBe('jwt-acces');

    // Comme après une connexion : le profil est chargé, ce qui met le menu à jour (défaut trouvé par le test E2E)
    const profil = http.expectOne('/api/auth/me');
    expect(profil.request.headers.get('Authorization')).toBe('Bearer jwt-acces');
    profil.flush({ id: '1', email: 'amina@test.ma', firstName: 'Amina', lastName: 'Test', role: 'PATIENT' });
    const auth = TestBed.inject(AuthService);
    expect(auth.connected()).toBe(true);
    expect(auth.profile()?.role).toBe('PATIENT');
  });

  it('signale un e-mail déjà utilisé', () => {
    page.register();
    http.expectOne('/api/auth/register').flush({}, { status: 409, statusText: 'Conflict' });
    expect(page.error()).toBe('Un compte existe déjà avec cette adresse e-mail.');
  });
});
