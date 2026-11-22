import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PharmacyRatingComponent } from './pharmacy-rating';

describe('US23 — notation après retrait', () => {
  let fixture: ComponentFixture<PharmacyRatingComponent>;
  let http: HttpTestingController;
  const url = '/api/availability-requests/request-1/reservations/reservation-1/rating';
  const saved = { id: 'note-1', reservationId: 'reservation-1', stars: 4, createdAt: '2026-11-20T12:00:00Z' };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    sessionStorage.setItem('access_token', 'patient-jwt');
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PharmacyRatingComponent);
    fixture.componentRef.setInput('requestId', 'request-1');
    fixture.componentRef.setInput('reservationId', 'reservation-1');
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); sessionStorage.clear(); });
  function empty() {
    const get = http.expectOne(url);
    expect(get.request.method).toBe('GET');
    expect(get.request.headers.get('Authorization')).toBe('Bearer patient-jwt');
    get.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
  }
  function select(star = 4) {
    const radio: HTMLInputElement = fixture.nativeElement.querySelectorAll('input[type=radio]')[star - 1];
    radio.click(); fixture.detectChanges();
    expect(fixture.componentInstance.selected()).toBe(star);
  }
  it('attend le chargement puis propose cinq étoiles accessibles et exige une sélection', () => {
    expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();
    empty();
    const radios = fixture.nativeElement.querySelectorAll('input[type=radio]');
    expect(radios.length).toBe(5);
    expect(radios[4].getAttribute('aria-label')).toBe('5 étoiles');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
    select(5);
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
  });
  it('envoie uniquement la note, bloque les doubles clics et confirme le succès', () => {
    empty(); select();
    fixture.componentInstance.submit(); fixture.componentInstance.submit();
    const post = http.expectOne(url);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ stars: 4 });
    expect(post.request.headers.get('Authorization')).toBe('Bearer patient-jwt');
    post.flush(saved, { status: 201, statusText: 'Created' }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Votre note a été enregistrée');
    expect(fixture.nativeElement.textContent).toContain('4 / 5 étoiles');
    expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();
    fixture.componentInstance.submit(); http.expectNone(url);
  });
  it('affiche la note existante sans autoriser de nouvelle soumission', () => {
    http.expectOne(url).flush(saved); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('4 / 5 étoiles');
    expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();
    fixture.componentInstance.submit(); http.expectNone(url);
  });
  it.each([400, 401, 403, 404, 500])('gère une erreur POST %s', status => {
    empty(); select(); fixture.componentInstance.submit();
    http.expectOne(url).flush({}, { status, statusText: 'Error' }); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect(fixture.componentInstance.sending()).toBe(false);
    expect(fixture.componentInstance.success()).toBe(false);
    if ([401, 403, 404].includes(status)) expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();
  });
  it('récupère la note enregistrée par un autre onglet après un conflit', () => {
    empty(); select(); fixture.componentInstance.submit();
    http.expectOne(url).flush({}, { status: 409, statusText: 'Conflict' });
    http.expectOne(url).flush(saved); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('4 / 5 étoiles');
    expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();
  });
  it('bloque le formulaire en cas d’erreur GET puis permet de réessayer', () => {
    http.expectOne(url).flush({}, { status: 503, statusText: 'Unavailable' }); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();
    fixture.nativeElement.querySelector('button').click(); empty();
    expect(fixture.nativeElement.querySelector('fieldset')).not.toBeNull();
  });
  it('annule une réponse devenue obsolète lors du changement de réservation', () => {
    const first = http.expectOne(url);
    fixture.componentRef.setInput('reservationId', 'reservation-2'); fixture.detectChanges();
    expect(first.cancelled).toBe(true);
    http.expectOne(url.replace('reservation-1', 'reservation-2')).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges(); expect(fixture.componentInstance.rating()).toBeNull();
  });
});
