import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { PharmacistStockPage } from './pharmacist-stock';
import { Produit } from '../services/ordonnance';

const DOLIPRANE = { id: '6118000000001', nom: 'DOLIPRANE 500 MG' } as Produit;

describe('PharmacistStockPage (US22)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.setItem('access_token', 'jeton-pharmacien');
    TestBed.configureTestingModule({
      imports: [PharmacistStockPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); sessionStorage.clear(); });

  function creer(pharmacies: { id: string; name: string }[]) {
    const f = TestBed.createComponent(PharmacistStockPage);
    f.detectChanges();
    http.expectOne('/api/pharmacies/me').flush(pharmacies);
    f.detectChanges();
    return f;
  }

  it('remet un médicament en stock dans ma pharmacie', () => {
    const c = creer([{ id: 'ph-1', name: 'Pharmacie Test' }]).componentInstance;
    expect(c.pharmacyId).toBe('ph-1');
    c.update(DOLIPRANE, 'AVAILABLE');
    const req = http.expectOne('/api/pharmacies/ph-1/stock/6118000000001');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'AVAILABLE' });
    expect(req.request.headers.get('Authorization')).toBe('Bearer jeton-pharmacien');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(c.success()).toContain('en stock');
    expect(c.history().length).toBe(1);
  });

  it('déclare une rupture', () => {
    const c = creer([{ id: 'ph-1', name: 'Pharmacie Test' }]).componentInstance;
    c.update(DOLIPRANE, 'OUT_OF_STOCK');
    const req = http.expectOne('/api/pharmacies/ph-1/stock/6118000000001');
    expect(req.request.body).toEqual({ status: 'OUT_OF_STOCK' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(c.success()).toContain('en rupture');
  });

  it('refuse un compte qui n’est pas celui de la pharmacie (403)', () => {
    const c = creer([{ id: 'ph-1', name: 'Pharmacie Test' }]).componentInstance;
    c.update(DOLIPRANE, 'AVAILABLE');
    http.expectOne('/api/pharmacies/ph-1/stock/6118000000001').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(c.error()).toContain('compte pharmacien');
  });

  it('indique quand aucune pharmacie n’est rattachée au compte', () => {
    const f = creer([]);
    expect(f.nativeElement.textContent).toContain('Aucune pharmacie');
  });
});
