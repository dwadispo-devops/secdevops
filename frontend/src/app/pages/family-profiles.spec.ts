import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FamilyProfilesPage } from './family-profiles';

describe('FamilyProfilesPage (US24)', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.setItem('access_token', 'patient-token');
    TestBed.configureTestingModule({ imports: [FamilyProfilesPage], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('liste les profils proches du patient', async () => {
    const fixture = TestBed.createComponent(FamilyProfilesPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/prescriptions/profiles').flush([{ id: 'profile-1', label: 'Maman', createdAt: '2026-11-19T10:00:00Z' }]);
    await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Maman');
    expect(fixture.nativeElement.textContent).toContain('Renommer');
  });

  it('crée un profil sans date de naissance', async () => {
    const fixture = TestBed.createComponent(FamilyProfilesPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/prescriptions/profiles').flush([]);
    const page = fixture.componentInstance;
    page.newLabel = 'Yassine'; page.create();
    const request = http.expectOne('/api/v1/prescriptions/profiles');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ label: 'Yassine' });
    request.flush({ id: 'profile-2', label: 'Yassine', createdAt: '2026-11-19T10:00:00Z' });
    await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Yassine');
  });
});
