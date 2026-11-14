import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { PrescriptionEstimateComponent } from './prescription-estimate';
import { LigneOrdonnance } from '../services/ordonnance';
import { PrescriptionEstimate } from '../services/prescription-estimate.service';

describe('US17 — estimation indicative', () => {
  const url = '/api/v1/prescriptions/estimate';
  let fixture: ComponentFixture<PrescriptionEstimateComponent>;
  let http: HttpTestingController;
  const line = (overrides: Partial<LigneOrdonnance> = {}): LigneOrdonnance => ({
    id: 1, texteLu: 'Produit', score: 1, suggestions: [], choix: { id: '6118000000001', nom: 'Produit, 1 BOITE' },
    quantite: 2, quantiteConfirmee: true, supprimee: false, ...overrides,
  });
  const result: PrescriptionEstimate = {
    currency: 'MAD', source: 'CNOPS 2014', calculatedAt: '2026-11-09T12:00:00Z', complete: true,
    knownSubtotal: 25.7, totalEstimatedCost: 25.7, reimbursableAmount: null, remainingCharge: null,
    reimbursementExplanation: 'La base de remboursement n’est pas confirmée.',
    lines: [{ code: '6118000000001', name: 'Produit', presentation: '1 BOITE', quantity: 2,
      unitPrice: 12.85, catalogRate: 70, estimatedCost: 25.7, reason: null }],
  };
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('access_token', 'patient-jwt');
    TestBed.configureTestingModule({ imports: [PrescriptionEstimateComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PrescriptionEstimateComponent);
    fixture.componentRef.setInput('lines', [line()]);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); sessionStorage.clear(); });
  const calculate = () => {
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    return http.expectOne(url);
  };

  it('envoie seulement les codes/quantités confirmés et la référence, jamais les prix', () => {
    sessionStorage.setItem('last_prescription_id', '83c4a4d2-ac2c-48df-9034-e49a582b1767');
    const request = calculate();
    expect(request.request.headers.get('Authorization')).toBe('Bearer patient-jwt');
    expect(request.request.body).toEqual({ prescriptionId: '83c4a4d2-ac2c-48df-9034-e49a582b1767',
      items: [{ code: '6118000000001', quantity: 2, unitConfirmed: true }] });
    request.flush(result);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('25.70 MAD');
    expect(fixture.nativeElement.textContent).toContain('Remboursement non calculable');
    expect(fixture.nativeElement.textContent).toContain('Reste à charge : non calculable');
    expect(fixture.nativeElement.textContent).toContain('70 %');
  });

  it.each([null, 0, -1, 1.5, 10001])('refuse la quantité %s sans inventer une boîte', quantity => {
    fixture.componentRef.setInput('lines', [line({ quantite: quantity })]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
    fixture.componentInstance.calculate();
    http.expectNone(url);
  });

  it('attend une confirmation de quantité et un produit catalogue réel', () => {
    for (const invalid of [line({ quantiteConfirmee: false }), line({ choix: null }), line({ choix: { id: 'p1', nom: 'Démo' } })]) {
      fixture.componentRef.setInput('lines', [invalid]); fixture.detectChanges();
      expect(fixture.componentInstance.ready()).toBe(false);
    }
    http.expectNone(url);
  });

  it('exclut les lignes supprimées et bloque une ordonnance vide', () => {
    fixture.componentRef.setInput('lines', [line(), line({ id: 2, supprimee: true })]); fixture.detectChanges();
    const request = calculate();
    expect(request.request.body.items).toHaveLength(1);
    request.flush(result);
    fixture.componentRef.setInput('lines', [line({ supprimee: true })]); fixture.detectChanges();
    expect(fixture.componentInstance.ready()).toBe(false);
    expect(fixture.componentInstance.estimate()).toBeNull();
  });

  it('efface une estimation après modification et annule une réponse devenue ancienne', () => {
    calculate().flush(result);
    fixture.componentRef.setInput('lines', [line({ quantite: 3 })]); fixture.detectChanges();
    expect(fixture.componentInstance.estimate()).toBeNull();
    const old = calculate();
    fixture.componentRef.setInput('lines', [line({ quantite: 4 })]); fixture.detectChanges();
    expect(old.cancelled).toBe(true);
    expect(fixture.componentInstance.loading()).toBe(false);
    const latest = calculate();
    expect(latest.request.body.items[0].quantity).toBe(4);
    latest.flush({ ...result, totalEstimatedCost: 51.4 });
    expect(fixture.componentInstance.estimate()?.totalEstimatedCost).toBe(51.4);
  });

  it('distingue un sous-total connu du total inconnu et un prix absent de zéro', () => {
    calculate().flush({ ...result, complete: false, totalEstimatedCost: null,
      lines: [{ ...result.lines[0], unitPrice: null, catalogRate: null, estimatedCost: null, reason: 'Prix public indisponible.' }] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Estimation incomplète');
    expect(fixture.nativeElement.textContent).toContain('Sous-total');
    expect(fixture.nativeElement.textContent).toContain('Indisponible');
    expect(fixture.nativeElement.textContent).toContain('Non renseigné');
  });

  it.each([0, 400, 401, 403, 404, 503])('gère l’erreur %s et permet de réessayer', status => {
    calculate().flush({}, { status, statusText: 'Error' }); fixture.detectChanges();
    expect(fixture.componentInstance.error()).not.toBe('');
    expect(fixture.componentInstance.estimate()).toBeNull();
    if (status === 401) expect(fixture.nativeElement.querySelector('a')).not.toBeNull();
    calculate().flush(result);
    expect(fixture.componentInstance.error()).toBe('');
    expect(fixture.componentInstance.estimate()).not.toBeNull();
  });

  it('demande une connexion sans requête si le jeton manque', () => {
    sessionStorage.removeItem('access_token');
    fixture.componentInstance.calculate(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a').textContent).toContain('Se connecter');
    http.expectNone(url);
  });
});
