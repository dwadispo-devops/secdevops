import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { StockAlertsPage } from './stock-alerts';
import { StockAlert } from '../services/stock-alert.service';
import { Produit } from '../services/ordonnance';

const ACTIVE: StockAlert = {
  id: 'a1', medicationCode: '6118000000001', medicationName: 'DOLIPRANE 500 MG', radiusM: 5000, status: 'ACTIVE',
  createdAt: '2026-11-16T10:00:00Z', triggeredAt: null, pharmacyId: null, pharmacyName: null, pharmacyAddress: null,
};
const DECLENCHEE: StockAlert = {
  ...ACTIVE, id: 'a2', medicationCode: '6118000000002', medicationName: 'AUGMENTIN 1 G', status: 'DECLENCHEE',
  triggeredAt: '2026-11-16T12:00:00Z', pharmacyId: 'p1', pharmacyName: 'Pharmacie Test', pharmacyAddress: 'Avenue Test',
};
const DOLIPRANE = { id: '6118000000001', nom: 'DOLIPRANE 500 MG' } as Produit;

function position(ok: boolean) {
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: {
      getCurrentPosition: (succes: PositionCallback, echec: PositionErrorCallback) =>
        ok ? succes({ coords: { latitude: 31.63, longitude: -8.0 } } as GeolocationPosition)
           : echec({ code: 1, message: 'refus' } as GeolocationPositionError),
    },
  });
}

describe('StockAlertsPage (US22)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.setItem('access_token', 'jeton-patient');
    TestBed.configureTestingModule({
      imports: [StockAlertsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); sessionStorage.clear(); });

  function creer(alertes: StockAlert[] = []) {
    const f = TestBed.createComponent(StockAlertsPage);
    f.detectChanges();
    const req = http.expectOne('/api/alertes/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jeton-patient');
    req.flush(alertes);
    f.detectChanges();
    return f;
  }

  it('affiche mes alertes en attente et déclenchées', () => {
    const texte: string = creer([ACTIVE, DECLENCHEE]).nativeElement.textContent;
    expect(texte).toContain('DOLIPRANE 500 MG');
    expect(texte).toContain('En attente · 5 km');
    expect(texte).toContain('Disponible');
    expect(texte).toContain('Pharmacie Test');
  });

  it('cherche le médicament dans le catalogue', () => {
    const c = creer().componentInstance;
    c.terme = 'dolip';
    c.search();
    http.expectOne((r) => r.url === '/api/medications/search')
      .flush([{ code: '6118000000001', nom: 'DOLIPRANE', dosageBrut: '500', uniteDosage1: 'MG', forme: 'COMPRIME' }]);
    expect(c.results().length).toBe(1);
    expect(c.results()[0].id).toBe('6118000000001');
  });

  it('crée une alerte avec ma position et le rayon choisi', async () => {
    position(true);
    const c = creer().componentInstance;
    c.choose(DOLIPRANE);
    c.radiusM = 10000;
    await c.create();
    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/alertes');
    expect(req.request.body).toEqual({
      medicationCode: '6118000000001', medicationName: 'DOLIPRANE 500 MG', latitude: 31.63, longitude: -8.0, radiusM: 10000,
    });
    req.flush({ ...ACTIVE, radiusM: 10000 });
    expect(c.alerts().length).toBe(1);
    expect(c.success()).toContain('Alerte créée');
  });

  it('signale une alerte en double (409)', async () => {
    position(true);
    const c = creer().componentInstance;
    c.choose(DOLIPRANE);
    await c.create();
    http.expectOne('/api/alertes').flush({}, { status: 409, statusText: 'Conflict' });
    expect(c.error()).toContain('déjà une alerte active');
  });

  it('n’envoie rien si la localisation est refusée', async () => {
    position(false);
    const c = creer().componentInstance;
    c.choose(DOLIPRANE);
    await c.create();
    http.expectNone('/api/alertes');
    expect(c.error()).toContain('position');
  });

  it('supprime une alerte', () => {
    const c = creer([ACTIVE, DECLENCHEE]).componentInstance;
    c.remove(ACTIVE);
    const req = http.expectOne('/api/alertes/a1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(c.alerts().map((a) => a.id)).toEqual(['a2']);
  });

  it('demande de se connecter en patient (401)', () => {
    const f = TestBed.createComponent(StockAlertsPage);
    f.detectChanges();
    http.expectOne('/api/alertes/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(f.componentInstance.error()).toContain('compte patient');
  });
});
