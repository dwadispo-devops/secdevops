import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { PharmacistRegisterPage } from './pharmacist-register';

describe('PharmacistRegisterPage (US03)', () => {
  let http: HttpTestingController;
  let page: PharmacistRegisterPage;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [PharmacistRegisterPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(PharmacistRegisterPage).componentInstance;
    Object.assign(page, { firstName: 'Youssef', lastName: 'Test', email: 'pharma@test.ma', password: 'Pharma123',
      confirm: 'Pharma123', name: 'Pharmacie Test', licenseNumber: 'AUT-001', address: 'Rue fictive, Marrakech' });
  });

  afterEach(() => http.verify());

  it('exige la position de la pharmacie', () => {
    expect(page.canSubmit()).toBe(false);
    page.latitude.set(31.64); page.longitude.set(-8.02);
    expect(page.canSubmit()).toBe(true);
  });

  it('crée le compte pharmacien, se connecte puis inscrit la pharmacie avec le jeton', () => {
    page.latitude.set(31.64); page.longitude.set(-8.02);
    page.inscrire();
    http.expectOne('/api/auth/register/pharmacien').flush({ id: 'u1', role: 'PHARMACIEN' });
    http.expectOne('/api/auth/login').flush({ accessToken: 'jwt-pharma' });
    const req = http.expectOne('/api/pharmacies');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-pharma');
    expect(req.request.body).toEqual(expect.objectContaining({ licenseNumber: 'AUT-001', latitude: 31.64, longitude: -8.02 }));
    req.flush({ id: 'p1', name: 'Pharmacie Test', status: 'PENDING' });
    expect(page.resultat()?.status).toBe('PENDING');
    expect(sessionStorage.getItem('access_token')).toBe('jwt-pharma');
  });

  it('ne recrée pas le compte si seule la pharmacie a échoué (licence déjà utilisée)', () => {
    page.latitude.set(31.64); page.longitude.set(-8.02);
    page.compteCree.set(true); sessionStorage.setItem('access_token', 'jwt-pharma');
    page.inscrire();
    http.expectOne('/api/pharmacies').flush({}, { status: 409, statusText: 'Conflict' });
    expect(page.error()).toContain('numéro d’autorisation');
  });
});
