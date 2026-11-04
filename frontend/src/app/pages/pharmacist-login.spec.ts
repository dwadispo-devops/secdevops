import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { PharmacistLoginPage } from './pharmacist-login';

describe('Pharmacist login', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] }));
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  it('uses the real Auth API and stores the access token only after success', () => {
    const page = TestBed.createComponent(PharmacistLoginPage).componentInstance;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    page.email = ' pharmacist@example.invalid '; page.password = 'Synthetic1'; page.login();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/auth/login');
    expect(request.request.body.email).toBe('pharmacist@example.invalid'); request.flush({ accessToken: 'test' });
    expect(sessionStorage.getItem('access_token')).toBe('test'); expect(page.password).toBe('');
    expect(navigate).toHaveBeenCalledWith('/pharmacien/demandes');
  });
  it('does not save a token after rejected credentials', () => {
    const page = TestBed.createComponent(PharmacistLoginPage).componentInstance;
    page.email = 'wrong@example.invalid'; page.password = 'Synthetic1'; page.login();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(sessionStorage.getItem('access_token')).toBeNull(); expect(page.error()).toContain('incorrects');
  });
});
