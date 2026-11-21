
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { PharmacistLoginPage } from './pharmacist-login';

describe('Pharmacist login', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('uses the real Auth API and stores the access token only after success', () => {
    const page = TestBed.createComponent(PharmacistLoginPage).componentInstance;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    page.email = ' pharmacist@example.invalid ';
    page.password = 'Synthetic1';
    page.login();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.body.email).toBe('pharmacist@example.invalid');

    request.flush({ accessToken: 'test' });

    expect(sessionStorage.getItem('access_token')).toBe('test');

    http.expectOne('/api/auth/me').flush({
      id: '1',
      email: 'pharmacist@example.invalid',
      firstName: 'Test',
      lastName: 'Pharmacist',
      role: 'PHARMACIEN',
      createdAt: '2026-11-17T00:00:00Z',
    });

    expect(page.password).toBe('');
    expect(navigate).toHaveBeenCalledWith('/pharmacien/demandes');
  });

  it('does not save a token after rejected credentials', () => {
    const page = TestBed.createComponent(PharmacistLoginPage).componentInstance;

    page.email = 'wrong@example.invalid';
    page.password = 'Synthetic1';
    page.login();

    http.expectOne('/api/auth/login').flush(
      {},
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(page.error()).toContain('incorrects');
  });

  it('refuse un compte ADMIN dans le formulaire réservé aux pharmaciens', () => {
    const page = TestBed.createComponent(PharmacistLoginPage).componentInstance;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    page.email = 'admin@example.invalid';
    page.password = 'Synthetic1';
    page.login();
    http.expectOne('/api/auth/login').flush({ accessToken: 'admin-token' });
    http.expectOne('/api/auth/me').flush({
      id: 'admin-1', email: 'admin@example.invalid', firstName: 'Admin', lastName: 'Test',
      role: 'ADMIN', createdAt: '2026-11-21T00:00:00Z',
    });
    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(page.error()).toContain('accès pharmacien');
    expect(navigate).not.toHaveBeenCalled();
  });
});
