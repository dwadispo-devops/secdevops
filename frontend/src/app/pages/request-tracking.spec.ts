import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CLE_SUIVI, ReponsePharmacie, RequestTrackingPage, trierReponses } from './request-tracking';

const ID = '83c4a4d2-ac2c-48df-9034-e49a582b1767';
function reponse(nom: string, status: string, price: number | null): ReponsePharmacie {
  return { requestId: ID, pharmacyId: nom, pharmacyName: nom, status, createdAt: '', prescriptionId: null, price, comment: null, respondedAt: null };
}

describe('RequestTrackingPage (suivi de la demande)', () => {
  let http: HttpTestingController;

  function creer(): RequestTrackingPage {
    TestBed.configureTestingModule({
      imports: [RequestTrackingPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(RequestTrackingPage).componentInstance;
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it('indique qu’il n’y a rien à suivre sans demande', () => {
    expect(creer().erreur()).toContain('Aucune demande');
  });

  it('charge les réponses avec le jeton du patient et place les pharmacies sur la carte', () => {
    sessionStorage.setItem(CLE_SUIVI, JSON.stringify({ id: ID, latitude: 31.64, longitude: -8.02, radiusM: 2000 }));
    sessionStorage.setItem('access_token', 'jwt');
    const page = creer();
    http.expectOne(r => r.url === '/api/pharmacies/nearby' && r.params.get('radius') === '2000').flush([
      { id: 'Ibn Nafis', name: 'Ibn Nafis', address: null, phone: null, openingHours: null, latitude: 31.64, longitude: -8.02, distanceM: 186 },
    ]);
    page.charger().subscribe();
    const req = http.expectOne(`/api/availability-requests/${ID}/responses`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt');
    req.flush([reponse('Atlas', 'PENDING', null), reponse('Ibn Nafis', 'AVAILABLE', 128.5)]);
    expect(page.triees()[0].pharmacyName).toBe('Ibn Nafis');
    expect(page.nb('AVAILABLE')).toBe(1);
    expect(page.enAttente().length).toBe(1);
    expect(page.points()).toEqual([expect.objectContaining({ id: 'Ibn Nafis', couleur: '#16a34a' })]);
  });

  it('explique qu’une demande d’un autre patient est introuvable (404)', () => {
    sessionStorage.setItem(CLE_SUIVI, JSON.stringify({ id: ID, latitude: 0, longitude: 0, radiusM: 0 }));
    sessionStorage.setItem('access_token', 'jwt');
    const page = creer();
    http.match(r => r.url === '/api/pharmacies/nearby').forEach(r => r.flush([]));
    page.charger().subscribe();
    http.expectOne(`/api/availability-requests/${ID}/responses`).flush({}, { status: 404, statusText: 'Not Found' });
    expect(page.erreur()).toBe('Demande introuvable pour ce compte.');
  });

  it('trie : disponibles du moins cher au plus cher, puis en attente, puis indisponibles', () => {
    const triees = trierReponses([reponse('C', 'UNAVAILABLE', null), reponse('B', 'AVAILABLE', 90),
      reponse('D', 'PENDING', null), reponse('A', 'AVAILABLE', 60)]);
    expect(triees.map(r => r.pharmacyName)).toEqual(['A', 'B', 'D', 'C']);
  });
});
