import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PharmacyService } from './pharmacy.service';

describe('PharmacyService (garde)', () => {
  let service: PharmacyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PharmacyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('appelle /api/pharmacies/garde sans position', () => {
    service.getOnDutyPharmacies().subscribe();
    const req = http.expectOne('/api/pharmacies/garde');
    expect(req.request.params.keys().length).toBe(0);
    req.flush({ date: '2026-11-11', pharmacies: [] });
  });

  it('envoie lat et lng quand la position est connue', () => {
    service.getOnDutyPharmacies(31.62, -7.98).subscribe();
    const req = http.expectOne((r) => r.url === '/api/pharmacies/garde');
    expect(req.request.params.get('lat')).toBe('31.62');
    expect(req.request.params.get('lng')).toBe('-7.98');
    req.flush({ date: '2026-11-11', pharmacies: [] });
  });
});
