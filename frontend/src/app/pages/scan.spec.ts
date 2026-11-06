import { TestBed, ComponentFixture } from '@angular/core/testing';
import { OrdonnanceService } from '../services/ordonnance';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ScanPage } from './scan';

/** Simule le choix d'un fichier dans un <input type="file">. */
function fileEvent(file: File): Event {
  return { target: { files: [file], value: 'C:\\fakepath\\' + file.name } } as unknown as Event;
}

function pdf(sizeBytes = 1000, name = 'ordonnance.pdf'): File {
  const content = new Uint8Array(sizeBytes);
  content.set([0x25, 0x50, 0x44, 0x46, 0x2d]); // "%PDF-"
  return new File([content], name, { type: 'application/pdf' });
}

describe('ScanPage (US06)', () => {
  let fixture: ComponentFixture<ScanPage>;
  let page: ScanPage;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [ScanPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ScanPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function element(selector: string): HTMLElement | null {
    fixture.detectChanges();
    return fixture.nativeElement.querySelector(selector);
  }

  it('propose de prendre une photo ou d’importer un fichier', () => {
    const camera = element('[data-testid="camera-input"]') as HTMLInputElement;
    const importer = element('[data-testid="file-input"]') as HTMLInputElement;

    expect(camera.hasAttribute('capture')).toBe(false); // le sélecteur s'ouvre aussi sur PC
    expect(camera.accept).toContain('image/');
    expect(importer.accept).toContain('.pdf');
    expect(fixture.nativeElement.textContent).toContain('Prendre une photo');
    expect(fixture.nativeElement.textContent).toContain('Importer un fichier');
  });

  it('n’active le bouton d’envoi qu’après le choix du fichier ET le consentement (RG06)', async () => {
    expect(element('[data-testid="submit"]')).toBeNull();

    await page.onFileSelected(fileEvent(pdf()));
    const submit = element('[data-testid="submit"]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    page.consent.set(true);
    expect((element('[data-testid="submit"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('refuse un format non autorisé (RG05)', async () => {
    await page.onFileSelected(fileEvent(new File(['texte'], 'notes.txt', { type: 'text/plain' })));

    expect(page.file()).toBeNull();
    expect(element('[role="alert"]')?.textContent).toContain('Format non accepté');
  });

  it('refuse un fichier de plus de 5 Mo (RG05)', async () => {
    await page.onFileSelected(fileEvent(pdf(6 * 1024 * 1024)));

    expect(page.file()).toBeNull();
    expect(element('[role="alert"]')?.textContent).toContain('5 Mo');
  });

  it('demande de se connecter s’il n’y a pas de jeton, sans appeler le serveur', async () => {
    await page.onFileSelected(fileEvent(pdf()));
    page.consent.set(true);

    page.submit();

    http.expectNone('/api/v1/prescriptions');
    expect(page.error()).toContain('Connectez-vous');
  });

  it('envoie l’ordonnance avec le jeton et le consentement, puis affiche la confirmation', async () => {
    sessionStorage.setItem('access_token', 'jeton-patient');
    await page.onFileSelected(fileEvent(pdf()));
    page.consent.set(true);

    page.submit();

    const request = http.expectOne('/api/v1/prescriptions');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe('Bearer jeton-patient');
    const body = request.request.body as FormData;
    expect(body.get('consent')).toBe('true');
    expect((body.get('file') as File).name).toBe('ordonnance.pdf');

    request.flush({ id: 'abc-123', status: 'UPLOADED', fileType: 'PDF', sizeBytes: 1000, createdAt: '2026-11-04T10:00:00Z' },
      { status: 201, statusText: 'Created' });

    expect(element('.success')?.textContent).toContain('Ordonnance envoyée');
    expect(element('.success')?.textContent).toContain('abc-123');
    expect(sessionStorage.getItem('last_prescription_id')).toBe('abc-123');
  });

  it('affiche un message clair si la session a expiré (401)', async () => {
    sessionStorage.setItem('access_token', 'jeton-expire');
    await page.onFileSelected(fileEvent(pdf()));
    page.consent.set(true);

    page.submit();
    http.expectOne('/api/v1/prescriptions').flush(
      { code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });

    expect(element('[role="alert"]')?.textContent).toContain('Reconnectez-vous');
    expect(page.file()).not.toBeNull(); // le patient n'a pas à re-choisir son fichier
  });

  it('affiche le message du serveur pour un fichier refusé (415)', async () => {
    sessionStorage.setItem('access_token', 'jeton-patient');
    await page.onFileSelected(fileEvent(pdf()));
    page.consent.set(true);

    page.submit();
    http.expectOne('/api/v1/prescriptions').flush(
      { code: 'FILE_TYPE_MISMATCH', detail: 'Le contenu du fichier ne correspond pas à son extension.' },
      { status: 415, statusText: 'Unsupported Media Type' });

    expect(page.error()).toBe('Le contenu du fichier ne correspond pas à son extension.');
  });

  it('requests actual stored OCR through the existing prescription service', () => {
    sessionStorage.setItem('access_token', 'patient'); page.readStored('prescription-id');
    const read = http.expectOne('/api/v1/prescriptions/prescription-id/read?consent=true');
    expect(read.request.headers.get('Authorization')).toBe('Bearer patient');
    read.flush({ readingId: 'r1', prescriptionDetected: true, warnings: [], provider: 'test', durationMs: 1,
      lignes: [{ id: 1, texteLu: 'DOLIPRANE 1 g', quantite: 2, score: 0.94, suggestions: [{ id: '611', nom: 'DOLIPRANE 1000 MG' }] }] });
    expect(page.readingMessage()).toContain('enregistrée');
    const lignes = TestBed.inject(OrdonnanceService).lignes();
    expect(lignes.length).toBe(1);
    expect(lignes[0].quantite).toBe(2);
    expect(lignes[0].choix?.nom).toBe('DOLIPRANE 1000 MG'); // vert : confirmé d'office
  });

  it('propose la saisie manuelle si aucune ligne n’a été lue (EF10)', () => {
    sessionStorage.setItem('access_token', 'patient'); page.readStored('prescription-id');
    http.expectOne('/api/v1/prescriptions/prescription-id/read?consent=true')
      .flush({ readingId: 'r1', prescriptionDetected: true, lignes: [], warnings: [], provider: 'test', durationMs: 1 });
    expect(page.readingFailed()).toBe(true);
    expect(TestBed.inject(OrdonnanceService).lignes().length).toBe(0);
  });

  it('reports unavailable OCR without a fake reading', () => {
    sessionStorage.setItem('access_token', 'patient'); page.readStored('prescription-id');
    http.expectOne('/api/v1/prescriptions/prescription-id/read?consent=true').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(page.readingMessage()).toContain('Aucun résultat'); expect(page.loading()).toBe(false);
  });

  it('permet d’envoyer une autre ordonnance après un envoi réussi', async () => {
    sessionStorage.setItem('access_token', 'jeton-patient');
    await page.onFileSelected(fileEvent(pdf()));
    page.consent.set(true);
    page.submit();
    http.expectOne('/api/v1/prescriptions').flush(
      { id: 'abc-123', status: 'UPLOADED', fileType: 'PDF', sizeBytes: 1000, createdAt: '2026-11-04T10:00:00Z' });

    page.restart();

    expect(page.result()).toBeNull();
    expect(page.file()).toBeNull();
    expect(page.consent()).toBe(false);
  });
});
