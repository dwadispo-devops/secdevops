import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PharmacistRequestsPage } from './pharmacist-requests';
import { PharmacyRequest } from '../services/pharmacist-review.service';
import { provideRouter } from '@angular/router';

describe('US10 pharmacist review', () => {
  let fixture: ComponentFixture<PharmacistRequestsPage>;
  let page: PharmacistRequestsPage;
  let http: HttpTestingController;
  const request: PharmacyRequest = { requestId: 'request-1', pharmacyId: 'pharmacy-1', pharmacyName: 'Pharmacie test',
    status: 'PENDING', createdAt: '2026-01-01T10:00:00Z', prescriptionId: 'prescription-1', price: null, comment: null, respondedAt: null };
  const base = '/api/pharmacies/pharmacy-1/requests/request-1';
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    sessionStorage.setItem('access_token', 'test-token');
    fixture = TestBed.createComponent(PharmacistRequestsPage); page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    vi.stubGlobal('URL', class extends URL {
      static override createObjectURL = vi.fn(() => 'blob:test');
      static override revokeObjectURL = vi.fn();
    });
    fixture.detectChanges();
    const list = http.expectOne('/api/pharmacies/me/requests');
    expect(list.request.headers.get('Authorization')).toBe('Bearer test-token'); list.flush([request]);
  });
  afterEach(() => { fixture.destroy(); http.verify(); sessionStorage.clear(); vi.unstubAllGlobals(); });
  function select(image = false, text: string | null = null, mime = 'image/png') {
    page.select(request);
    http.expectOne(base).flush({ request, medications: [{ name: 'Médicament patient', quantity: 2 }] });
    http.expectOne(base + '/prescription').flush({ imageAvailable: image, mediaType: mime, ocrText: text,
      medications: text ? [{ rawText: text, name: 'Médicament reconnu', dosage: '1 g', status: 'LU' }] : [] });
    if (image) http.expectOne(base + '/prescription/image').flush(new Blob(['synthetic'], { type: mime }));
    fixture.detectChanges();
  }
  it('renders the real inbox, detail and explicit missing OCR', () => {
    select();
    expect(fixture.nativeElement.textContent).toContain('Pharmacie test');
    expect(fixture.nativeElement.textContent).toContain('Médicament patient');
    expect(fixture.nativeElement.textContent).toContain('Aucun texte OCR disponible');
    expect(fixture.nativeElement.textContent).toContain('Image non fournie ou supprimée');
  });
  it('displays protected original image, OCR and recognized medications', () => {
    select(true, 'Texte OCR existant');
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('blob:test');
    expect(fixture.nativeElement.textContent).toContain('Texte OCR existant');
    expect(fixture.nativeElement.textContent).toContain('Médicament reconnu');
    fixture.destroy(); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test');
  });
  it('offers a protected PDF link', () => {
    select(true, null, 'application/pdf');
    expect(fixture.nativeElement.querySelector('a[target="_blank"]').textContent).toContain('PDF');
  });
  it.each(['', '-1', '1.234', 'NaN', 'Infinity', '10000000000'])('rejects invalid price %s before HTTP', price => {
    select(); page.price = price; page.submit(); expect(page.valid()).toBe(false);
    http.expectNone(base + '/response');
  });
  it('rejects overly long comments', () => {
    select(); page.price = '0'; page.comment = 'a'.repeat(1001); page.submit(); http.expectNone(base + '/response');
  });
  it('submits valid availability, prevents duplicate clicks and shows confirmation', () => {
    select(); page.price = '12.50'; page.comment = ' Prêt '; page.submit(); page.submit();
    const post = http.expectOne(base + '/response');
    expect(post.request.body).toEqual({ availability: 'AVAILABLE', price: 12.5, comment: 'Prêt' });
    post.flush({ ...request, status: 'AVAILABLE', price: 12.5 }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Votre réponse a été enregistrée');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });
  it('submits unavailable with no price even after a price was entered', () => {
    select(); page.price = '12'; page.availability = 'UNAVAILABLE'; page.submit();
    const post = http.expectOne(base + '/response'); expect(post.request.body.price).toBeNull();
    post.flush({ ...request, status: 'UNAVAILABLE' });
  });
  it.each([400, 401, 403, 404, 409, 503])('shows HTTP error %s without false success', status => {
    select(); page.price = '0'; page.submit();
    http.expectOne(base + '/response').flush({}, { status, statusText: 'Error' });
    expect(page.error()).not.toBe(''); expect(page.success()).toBe(''); expect(page.sending()).toBe(false);
  });
  it('keeps response available when prescription service is offline', () => {
    page.select(request); http.expectOne(base).flush({ request, medications: [] });
    http.expectOne(base + '/prescription').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Service ordonnance indisponible');
    page.availability = 'UNAVAILABLE'; expect(page.valid()).toBe(true);
  });
  it('displays an image deletion error', () => {
    page.select(request); http.expectOne(base).flush({ request, medications: [] });
    http.expectOne(base + '/prescription').flush({ imageAvailable: true, mediaType: 'image/png', ocrText: null, medications: [] });
    http.expectOne(base + '/prescription/image').flush(null, { status: 404, statusText: 'Not found' });
    expect(page.imageError()).toContain('indisponible');
  });
  it('cancels stale detail and prescription requests when selection changes', () => {
    page.select(request); const old = http.match(r => r.url.startsWith(base));
    const other = { ...request, requestId: 'request-2' }; page.select(other);
    expect(old.every(r => r.cancelled)).toBe(true);
    http.expectOne(base.replace('request-1', 'request-2')).flush({ request: other, medications: [] });
    http.expectOne(base.replace('request-1', 'request-2') + '/prescription').flush({ imageAvailable: false, ocrText: null, medications: [] });
    expect(page.detail()?.request.requestId).toBe('request-2');
  });
});
