import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { AvailabilityRequestPage } from './availability-request';

describe('AvailabilityRequestPage', () => {
  let page: AvailabilityRequestPage;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    });
    page = TestBed.createComponent(AvailabilityRequestPage).componentInstance;
    http = TestBed.inject(HttpTestingController);
    sessionStorage.clear();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function validForm() {
    page.medications = [{ name: ' Doliprane ', quantity: 2 }, { name: 'Vitamine C', quantity: 1 }];
    page.latitude.set(31.63);
    page.longitude.set(-8);
    sessionStorage.setItem('access_token', 'patient-test-token');
  }

  it('adds and removes medicines while retaining one row', () => {
    page.addMedication();
    expect(page.medications).toHaveLength(2);
    page.removeMedication(0);
    page.removeMedication(0);
    expect(page.medications).toHaveLength(1);
  });

  it('requires GPS and patient authentication before sending', () => {
    page.submit();
    expect(page.message()).toContain('GPS');
    validForm();
    sessionStorage.clear();
    page.submit();
    expect(page.message()).toContain('compte patient');
    http.expectNone('/api/availability-requests');
  });

  it.each([99, 20001, 100.5, NaN])('rejects invalid radius %s', radius => {
    validForm();
    page.radiusM = radius;
    page.submit();
    http.expectNone('/api/availability-requests');
    expect(page.loading()).toBe(false);
  });

  it('rejects empty medicines, blank names, fractional quantities and invalid coordinates', () => {
    validForm();
    for (const medications of [[], [{ name: ' ', quantity: 1 }], [{ name: 'A', quantity: 1.5 }]]) {
      page.medications = medications;
      page.submit();
      http.expectNone('/api/availability-requests');
    }
    validForm();
    page.latitude.set(NaN);
    page.submit();
    http.expectNone('/api/availability-requests');
  });

  it.each([100, 20000])('posts multiple medicines with bearer JWT at radius %s', radius => {
    validForm();
    page.radiusM = radius;
    page.submit();
    page.submit(); // A second click must not create a duplicate request.
    const request = http.expectOne('/api/availability-requests');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe('Bearer patient-test-token');
    expect(request.request.body).toEqual({
      latitude: 31.63, longitude: -8, radiusM: radius,
      medications: [{ name: 'Doliprane', quantity: 2 }, { name: 'Vitamine C', quantity: 1 }],
    });
    request.flush({ status: 'PENDING' }, { status: 201, statusText: 'Created' });
    expect(page.loading()).toBe(false);
    expect(page.message()).toContain('succès');
  });

  it.each([401, 500])('shows HTTP error %s and permits retry', status => {
    validForm();
    page.submit();
    http.expectOne('/api/availability-requests').flush('error', { status, statusText: 'Error' });
    expect(page.loading()).toBe(false);
    expect(page.message()).toContain(status === 401 ? 'expirée' : 'connexion');
    page.submit();
    http.expectOne('/api/availability-requests').flush({ status: 'PENDING' });
    expect(page.message()).toContain('succès');
  });

  it('handles GPS success, denial and unavailable geolocation', () => {
    const getCurrentPosition = vi.fn((success: PositionCallback) => {
      success({ coords: { latitude: 31.63, longitude: -8 } } as GeolocationPosition);
    });
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } });
    page.getLocation();
    expect(page.latitude()).toBe(31.63);
    expect(page.longitude()).toBe(-8);
    getCurrentPosition.mockImplementation((_success, error?: PositionErrorCallback) => {
      error?.({ code: 1 } as GeolocationPositionError);
    });
    page.getLocation();
    expect(page.message()).toContain('Impossible');
    vi.stubGlobal('navigator', {});
    page.getLocation();
    expect(page.message()).toContain('indisponible');
  });

  it('renders the lazy route and form controls', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/demande-disponibilite', AvailabilityRequestPage);
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Demande de disponibilité');
    expect(harness.routeNativeElement?.querySelector('input[name="radius"]')).toBeTruthy();
  });

  it.each([
    ['name too long', 'Q'.repeat(201), 1],
    ['zero quantity', 'Doliprane', 0],
    ['negative quantity', 'Doliprane', -1],
    ['non-finite quantity', 'Doliprane', Infinity],
  ])('rejects %s before HTTP', (_scenario, name, quantity) => {
    validForm();
    page.medications = [{ name: name as string, quantity: quantity as number }];
    page.submit();
    http.expectNone('/api/availability-requests');
    expect(page.message()).toContain('Vérifiez');
  });

  it.each([
    [-90.01, 0], [90.01, 0], [0, -180.01], [0, 180.01],
    [Infinity, 0], [0, NaN], [0, Infinity],
  ])('rejects coordinates (%s, %s) before HTTP', (latitude, longitude) => {
    validForm();
    page.latitude.set(latitude);
    page.longitude.set(longitude);
    page.submit();
    http.expectNone('/api/availability-requests');
    expect(page.loading()).toBe(false);
    expect(page.message()).toContain('Vérifiez');
  });

  it('binds DOM inputs, submits the form and displays the response', async () => {
    const fixture = TestBed.createComponent(AvailabilityRequestPage);
    const component = fixture.componentInstance;
    component.latitude.set(0);
    component.longitude.set(0);
    sessionStorage.setItem('access_token', 'patient-test-token');
    fixture.detectChanges();
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const inputs = root.querySelectorAll<HTMLInputElement>('.medication input');
    inputs[0].value = 'Q'.repeat(200);
    inputs[0].dispatchEvent(new Event('input'));
    inputs[1].value = '3';
    inputs[1].dispatchEvent(new Event('input'));
    await fixture.whenStable();
    root.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    expect(root.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
    const request = http.expectOne('/api/availability-requests');
    expect(request.request.body.medications).toEqual([{ name: 'Q'.repeat(200), quantity: 3 }]);
    expect(request.request.body.latitude).toBe(0);
    expect(request.request.body.longitude).toBe(0);
    request.flush({ status: 'PENDING' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    expect(root.querySelector('[role="status"]')?.textContent).toContain('succès');
    expect(root.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });
});
