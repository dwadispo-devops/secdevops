import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { PharmacistDutiesPage } from './pharmacist-duties';

describe('PharmacistDutiesPage (US15 – mes gardes)', () => {
  let http: HttpTestingController;

  const duty = {
    id: 'd1', pharmacyId: 'p1', pharmacyName: 'Pharmacie Démo',
    startsOn: '2099-01-01', endsOn: '2099-01-03',
  };

  async function start() {
    TestBed.configureTestingModule({
      imports: [PharmacistDutiesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PharmacistDutiesPage);
    fixture.detectChanges();
    http.expectOne('/api/pharmacies/me').flush([{ id: 'p1', name: 'Pharmacie Démo' }]);
    http.expectOne('/api/pharmacies/me/gardes').flush([duty]);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('affiche les pharmacies et les gardes déclarées', async () => {
    const fixture = await start();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Pharmacie Démo');
    expect(text).toContain('du 01/01/2099 au 03/01/2099');
    expect(text).toContain('À venir');
  });

  it('envoie la nouvelle garde au serveur', async () => {
    const fixture = await start();
    const page = fixture.componentInstance;
    page.startsOn = '2099-02-01';
    page.endsOn = '2099-02-02';
    page.submit();
    const req = http.expectOne('/api/pharmacies/me/gardes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ pharmacyId: 'p1', startsOn: '2099-02-01', endsOn: '2099-02-02' });
    req.flush({ ...duty, id: 'd2', startsOn: '2099-02-01', endsOn: '2099-02-02' });
    expect(page.duties().length).toBe(2);
  });

  it('refuse une fin avant le début sans appeler le serveur', async () => {
    const fixture = await start();
    const page = fixture.componentInstance;
    page.startsOn = '2099-02-05';
    page.endsOn = '2099-02-01';
    page.submit();
    expect(page.error()).toContain('postérieure');
  });

  it('explique un chevauchement (409)', async () => {
    const fixture = await start();
    const page = fixture.componentInstance;
    page.submit();
    http.expectOne('/api/pharmacies/me/gardes').flush({}, { status: 409, statusText: 'Conflict' });
    expect(page.error()).toContain('déjà une garde');
  });
});
