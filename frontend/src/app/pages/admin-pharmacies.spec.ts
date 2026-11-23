import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminPharmaciesPage } from './admin-pharmacies';

describe('AdminPharmaciesPage', () => {
  let http: HttpTestingController;
  const pending = {
    id: 'pharmacy-1', name: 'Pharmacie Atlas', address: 'Casablanca',
    licenseNumber: 'AUT-21', createdAt: '2026-11-20T09:00:00Z',
  };

  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('access_token', 'admin-token');
    TestBed.configureTestingModule({
      imports: [AdminPharmaciesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('charge et affiche uniquement les champs de vérification', () => {
    const fixture = TestBed.createComponent(AdminPharmaciesPage);
    fixture.detectChanges();
    const request = http.expectOne('/api/pharmacies/admin/pending');
    expect(request.request.headers.get('Authorization')).toBe('Bearer admin-token');
    request.flush([pending]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Pharmacie Atlas');
    expect(text).toContain('Casablanca');
    expect(text).toContain('AUT-21');
    expect(text).toContain('2026');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('li')).toHaveLength(1);
  });

  it('affiche l’état vide quand aucune pharmacie n’attend', () => {
    const fixture = TestBed.createComponent(AdminPharmaciesPage);
    fixture.detectChanges();
    http.expectOne('/api/pharmacies/admin/pending').flush([]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucune pharmacie en attente');
  });

  it('désactive le bouton pendant la validation et retire la pharmacie après 200', () => {
    const fixture = TestBed.createComponent(AdminPharmaciesPage);
    fixture.detectChanges();
    http.expectOne('/api/pharmacies/admin/pending').flush([pending]);
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector('li button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    const request = http.expectOne('/api/pharmacies/admin/pharmacy-1/validate');
    expect(request.request.method).toBe('POST');
    request.flush(pending);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('li')).toBeNull();
  });

  it('affiche un message français selon le code d’erreur', () => {
    const fixture = TestBed.createComponent(AdminPharmaciesPage);
    fixture.detectChanges();
    http.expectOne('/api/pharmacies/admin/pending').flush([pending]);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    for (const [status, message] of [
      [401, 'Votre session a expiré'],
      [403, 'réservée à un administrateur'],
      [404, 'introuvable'],
      [409, 'déjà été traitée'],
    ] as const) {
      page.validate(pending);
      http.expectOne('/api/pharmacies/admin/pharmacy-1/validate').flush({}, { status, statusText: 'Erreur' });
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain(message);
    }
  });
});
