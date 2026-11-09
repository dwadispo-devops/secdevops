import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { CLE_SUIVI, ReponsePharmacie, Reservation, RequestTrackingPage, trierReponses } from './request-tracking';

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
    page.chargerReservation().subscribe();
    http.expectOne(`/api/availability-requests/${ID}/reservation`).flush({}, { status: 404, statusText: 'Not Found' });
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

  it('réserve chez une pharmacie disponible et affiche le code de retrait (US12)', () => {
    sessionStorage.setItem(CLE_SUIVI, JSON.stringify({ id: ID, latitude: 0, longitude: 0, radiusM: 0 }));
    sessionStorage.setItem('access_token', 'jwt');
    const page = creer();
    page.chargerReservation().subscribe();
    http.expectOne(`/api/availability-requests/${ID}/reservation`).flush({}, { status: 404, statusText: 'Not Found' });
    http.match(r => r.url === '/api/pharmacies/nearby').forEach(r => r.flush([]));
    page.reserver(reponse('Ibn Nafis', 'AVAILABLE', 128.5));
    const req = http.expectOne(`/api/availability-requests/${ID}/reservations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ pharmacyId: 'Ibn Nafis' });
    req.flush({ id: 'r1', requestId: ID, pharmacyId: 'Ibn Nafis', pharmacyName: 'Ibn Nafis', code: 'K7P2QX',
      status: 'ACTIVE', price: 128.5, createdAt: '', expiresAt: new Date(Date.now() + 7200000).toISOString() });
    expect(page.reservation()?.code).toBe('K7P2QX');
    expect(page.reservationActive()).toBe(true);
    page.reserver(reponse('Ibn Nafis', 'AVAILABLE', 128.5)); // déjà active : aucun nouvel appel
  });

  it('affiche le message du serveur si la réservation est refusée (RG04)', () => {
    sessionStorage.setItem(CLE_SUIVI, JSON.stringify({ id: ID, latitude: 0, longitude: 0, radiusM: 0 }));
    sessionStorage.setItem('access_token', 'jwt');
    const page = creer();
    page.chargerReservation().subscribe();
    http.expectOne(`/api/availability-requests/${ID}/reservation`).flush({}, { status: 404, statusText: 'Not Found' });
    http.match(r => r.url === '/api/pharmacies/nearby').forEach(r => r.flush([]));
    page.reserver(reponse('Ibn Nafis', 'AVAILABLE', 128.5));
    http.expectOne(`/api/availability-requests/${ID}/reservations`)
      .flush({ message: 'Vous avez déjà 3 réservations actives' }, { status: 409, statusText: 'Conflict' });
    expect(page.erreurReservation()).toContain('3 réservations actives');
    expect(page.reservation()).toBeNull();
  });
});

describe('US12 : rendu du suivi et expiration', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<RequestTrackingPage>;
  const url = `/api/availability-requests/${ID}`;
  const active = (overrides: Partial<Reservation> = {}): Reservation => ({
    id: 'r1', requestId: ID, pharmacyId: 'ph1', pharmacyName: 'Pharmacie du Centre',
    code: 'K7P2QX', status: 'ACTIVE', price: 128.5,
    createdAt: new Date(Date.now()).toISOString(), expiresAt: new Date(Date.now() + 7200000).toISOString(),
    ...overrides,
  });
  const reservationResponse = (res: Reservation | null) => {
    const req = http.expectOne(`${url}/reservation`);
    if (res) req.flush(res);
    else req.flush({ message: 'Aucune réservation' }, { status: 404, statusText: 'Not Found' });
  };
  function start(res: Reservation | null = null) {
    vi.advanceTimersByTime(0);
    http.expectOne(`${url}/responses`).flush([reponse('ph1', 'AVAILABLE', 128.5)]);
    reservationResponse(res);
    fixture.detectChanges();
  }
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-09T10:00:00Z'));
    sessionStorage.clear();
    sessionStorage.setItem('access_token', 'patient-jwt');
    TestBed.configureTestingModule({
      imports: [RequestTrackingPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: ID }) } } }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RequestTrackingPage);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.useRealTimers();
  });

  it('attend la consultation initiale, réserve et rend le QR, le texte et la date au Maroc', () => {
    expect(fixture.componentInstance.reservationChargee()).toBe(false);
    start();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.liste button');
    expect(button.disabled).toBe(false);
    button.click();
    const req = http.expectOne(`${url}/reservations`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer patient-jwt');
    expect(req.request.body).toEqual({ pharmacyId: 'ph1' });
    req.flush(active());
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-reservation-qr svg')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.code').textContent).toContain('K7P2QX');
    expect(fixture.nativeElement.textContent).toContain('09/11/2026 13:00:00');
    expect(fixture.nativeElement.textContent).toContain('heure du Maroc');
    expect(button.disabled).toBe(true);
  });

  it('restaure le QR depuis le serveur à l’ouverture directe du suivi', () => {
    start(active());
    expect(fixture.nativeElement.querySelector('app-reservation-qr svg')).not.toBeNull();
  });

  it('masque le QR à l’échéance locale sans attendre une réponse réseau', () => {
    start(active({ expiresAt: new Date(Date.now() + 1000).toISOString() }));
    expect(fixture.nativeElement.querySelector('svg')).not.toBeNull();
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(fixture.componentInstance.reservationActive()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-reservation-qr')).toBeNull();
    expect(fixture.nativeElement.querySelector('.code')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Réservation expirée');
    expect(fixture.nativeElement.textContent).toContain('dans les deux heures');
  });

  it.each(['EXPIREE', 'RETIREE', 'ANNULEE'])('ne rend aucun QR pour le statut %s', status => {
    start(active({ status }));
    expect(fixture.nativeElement.querySelector('app-reservation-qr')).toBeNull();
    expect(fixture.nativeElement.querySelector('.code')).toBeNull();
  });

  it('retire le QR quand le serveur signale un retrait lors du rafraîchissement', () => {
    start(active());
    vi.advanceTimersByTime(10000);
    http.expectOne(`${url}/responses`).flush([]);
    reservationResponse(active({ status: 'RETIREE' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-reservation-qr')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Médicaments retirés');
  });

  it.each([0, 500, 401, 403])('signale une erreur de consultation %s puis permet la récupération', status => {
    start(active());
    fixture.componentInstance.chargerReservation().subscribe();
    http.expectOne(`${url}/reservation`).flush({}, { status, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-reservation-qr')).toBeNull();
    expect(fixture.componentInstance.reservationChargee()).toBe(false);
    expect(fixture.componentInstance.erreurChargementReservation()).not.toBe('');
    if (status === 401) expect(fixture.nativeElement.querySelector('a[href^="/connexion"]')).not.toBeNull();
    fixture.componentInstance.chargerReservation().subscribe();
    reservationResponse(active());
    fixture.detectChanges();
    expect(fixture.componentInstance.erreurChargementReservation()).toBe('');
    expect(fixture.nativeElement.querySelector('app-reservation-qr svg')).not.toBeNull();
  });

  it('ne confond pas une panne avec l’absence de réservation et bloque une création', () => {
    vi.advanceTimersByTime(0);
    http.expectOne(`${url}/responses`).flush([reponse('ph1', 'AVAILABLE', 128.5)]);
    http.expectOne(`${url}/reservation`).flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.liste button').disabled).toBe(true);
    fixture.componentInstance.reserver(reponse('ph1', 'AVAILABLE', 128.5));
    http.expectNone(`${url}/reservations`);
  });

  it('efface une ancienne réservation lorsque le serveur renvoie 404', () => {
    start(active());
    fixture.componentInstance.chargerReservation().subscribe();
    reservationResponse(null);
    fixture.detectChanges();
    expect(fixture.componentInstance.reservation()).toBeNull();
    expect(fixture.componentInstance.erreurChargementReservation()).toBe('');
    expect(fixture.nativeElement.querySelector('.liste button').disabled).toBe(false);
  });

  it('ignore une ancienne consultation terminée après la création', () => {
    start();
    fixture.componentInstance.chargerReservation().subscribe();
    fixture.componentInstance.reserver(reponse('ph1', 'AVAILABLE', 128.5));
    http.expectOne(`${url}/reservations`).flush(active());
    reservationResponse(null);
    expect(fixture.componentInstance.reservation()?.code).toBe('K7P2QX');
  });

  it('efface les données si la session disparaît', () => {
    start(active());
    sessionStorage.removeItem('access_token');
    fixture.componentInstance.charger().subscribe();
    fixture.componentInstance.chargerReservation().subscribe();
    fixture.detectChanges();
    expect(fixture.componentInstance.reservation()).toBeNull();
    expect(fixture.componentInstance.reponses()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-reservation-qr')).toBeNull();
  });
});
