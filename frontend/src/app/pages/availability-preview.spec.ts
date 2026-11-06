import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AvailabilityRequestPage } from './availability-request';
import { Pharmacy } from '../services/pharmacy.service';

describe('Aperçu des pharmacies destinataires (US09)', () => {
  let http: HttpTestingController;
  let page: AvailabilityRequestPage;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AvailabilityRequestPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(AvailabilityRequestPage).componentInstance;
  });

  afterEach(() => http.verify());

  it('ne cherche rien tant que la position est inconnue', () => {
    let resultat: Pharmacy[] | null | undefined;
    page.chargerApercu().subscribe(r => resultat = r);
    expect(resultat).toBeNull();
  });

  it('ne cherche rien si le rayon est hors limites', () => {
    page.latitude.set(31.64); page.longitude.set(-8.02); page.radiusM = 50;
    let resultat: Pharmacy[] | null | undefined;
    page.chargerApercu().subscribe(r => resultat = r);
    expect(resultat).toBeNull();
  });

  it('interroge /nearby avec la position et le rayon choisis', () => {
    page.latitude.set(31.64); page.longitude.set(-8.02); page.radiusM = 2000;
    let resultat: Pharmacy[] | null = null;
    page.chargerApercu().subscribe(r => resultat = r);
    http.expectOne(r => r.url === '/api/pharmacies/nearby' && r.params.get('radius') === '2000' && r.params.get('lat') === '31.64')
      .flush([{ id: '1', name: 'Pharmacie Ibn Nafis', address: null, phone: null, openingHours: null, latitude: 31.64, longitude: -8.02, distanceM: 186 }]);
    expect(resultat!.length).toBe(1);
  });

  it('affiche des distances lisibles', () => {
    expect(page.distanceLisible(186.4)).toBe('186 m');
    expect(page.distanceLisible(2140)).toBe('2,1 km');
  });
});
