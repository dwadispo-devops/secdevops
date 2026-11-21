import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { ProfileService, Profile } from './profile.service';
import { roleGuard } from './auth.guard';

describe('roleGuard', () => {
  const profile = (role: Profile['role']) => ({
    id: 'user-1', email: 'test@example.invalid', firstName: 'Test', lastName: 'User',
    role, createdAt: '2026-11-21T00:00:00Z',
  });

  async function destination(role: Profile['role'], allowed: Profile['role'][], url: string) {
    sessionStorage.setItem('access_token', 'test-token');
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ProfileService, useValue: { get: () => of(profile(role)) } }],
    });
    const result = TestBed.runInInjectionContext(() => roleGuard(...allowed)(null!, { url } as never));
    const value = result instanceof Promise ? await result : result instanceof Object && 'subscribe' in result
      ? await firstValueFrom(result as import('rxjs').Observable<boolean | import('@angular/router').UrlTree>)
      : result;
    const router = TestBed.inject(Router);
    const destination = value === true ? true : router.serializeUrl(value as import('@angular/router').UrlTree);
    TestBed.resetTestingModule();
    return destination;
  }

  afterEach(() => { sessionStorage.clear(); TestBed.resetTestingModule(); });

  it('autorise ADMIN sur son écran', async () => {
    expect(await destination('ADMIN', ['ADMIN'], '/admin/pharmacies')).toBe(true);
  });

  it('redirige ADMIN vers son écran si un autre rôle est demandé', async () => {
    expect(await destination('ADMIN', ['PATIENT'], '/scan')).toBe('/admin/pharmacies');
  });

  it('conserve les redirections patient et pharmacien', async () => {
    expect(await destination('PATIENT', ['ADMIN'], '/admin/pharmacies')).toBe('/scan');
    expect(await destination('PHARMACIEN', ['ADMIN'], '/admin/pharmacies')).toBe('/pharmacien/demandes');
  });
});
