import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { OrdonnanceService } from '../services/ordonnance';
import { ScanPage } from './scan';
import { VerificationPage } from './verification';

/** US26 / EF10 – Si la lecture échoue, je veux reprendre la photo ou saisir le médicament à la main. */
describe('US26 — reprendre la photo ou saisir à la main', () => {
  let http: HttpTestingController;
  let ordonnance: OrdonnanceService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [ScanPage, VerificationPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    ordonnance = TestBed.inject(OrdonnanceService);
  });

  afterEach(() => http.verify());

  const DOLIPRANE = { id: '6118000040972', nom: 'DOLIPRANE 1 G, COMPRIME SECABLE, 1 BOITE 10 COMPRIME' };

  describe('OrdonnanceService', () => {
    it('ajoute un médicament du catalogue, confirmé, avec une quantité à confirmer', () => {
      ordonnance.charger([{ id: 1, texteLu: 'SPASFON 80 mg', score: 0.95, suggestions: [{ id: '6118010000010', nom: 'SPASFON 80 MG' }] }]);

      ordonnance.ajouterManuel(DOLIPRANE);

      const ajoutee = ordonnance.lignes()[1];
      expect(ajoutee).toMatchObject({ id: 2, choix: DOLIPRANE, manuelle: true, quantite: 1, quantiteConfirmee: false });
      expect(ordonnance.peutContinuer()).toBe(true);
    });

    it('permet une ordonnance entièrement saisie à la main', () => {
      ordonnance.vider();
      expect(ordonnance.peutContinuer()).toBe(false);

      ordonnance.ajouterManuel(DOLIPRANE);

      expect(ordonnance.lignes()).toHaveLength(1);
      expect(ordonnance.peutContinuer()).toBe(true);
    });
  });

  describe('Page Scanner', () => {
    function lectureEchouee() {
      const fixture = TestBed.createComponent(ScanPage);
      const page = fixture.componentInstance;
      fixture.detectChanges(); // ngOnInit sans jeton : pas de chargement des profils famille
      sessionStorage.setItem('access_token', 'patient');
      page.readStored('prescription-id');
      http.expectOne('/api/v1/prescriptions/prescription-id/read?consent=true')
        .flush({ readingId: 'r1', prescriptionDetected: true, lignes: [], warnings: [], provider: 'test', durationMs: 1 });
      page.result.set({ id: 'prescription-id' } as never);
      fixture.detectChanges();
      return { fixture, page };
    }

    it('propose les deux choix quand la lecture échoue', () => {
      const { fixture } = lectureEchouee();
      const html = fixture.nativeElement as HTMLElement;

      expect(html.querySelector('[data-testid="retake"]')?.textContent).toContain('Reprendre la photo');
      expect(html.querySelector('[data-testid="manual-entry"]')?.textContent).toContain('Saisir mes médicaments à la main');
    });

    it('« Reprendre la photo » revient à l’écran de prise de vue', () => {
      const { fixture, page } = lectureEchouee();

      (fixture.nativeElement.querySelector('[data-testid="retake"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(page.result()).toBeNull();
      expect(page.readingFailed()).toBe(false);
      expect(fixture.nativeElement.textContent).toContain('Prendre une photo');
    });

    it('« Saisir à la main » ouvre la vérification avec une ordonnance vide', () => {
      ordonnance.charger([{ id: 1, texteLu: 'ancienne ligne', score: 0.3, suggestions: [] }]);
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      const { fixture } = lectureEchouee();

      (fixture.nativeElement.querySelector('[data-testid="manual-entry"]') as HTMLButtonElement).click();

      expect(ordonnance.lignes()).toHaveLength(0);
      expect(navigate).toHaveBeenCalledWith('/verification');
    });

    it('propose aussi la saisie manuelle si le serveur est indisponible à l’envoi (ENF07)', async () => {
      const fixture = TestBed.createComponent(ScanPage);
      const page = fixture.componentInstance;
      fixture.detectChanges(); // ngOnInit sans jeton : pas de chargement des profils famille
      sessionStorage.setItem('access_token', 'patient');
      const content = new Uint8Array(1000);
      content.set([0x25, 0x50, 0x44, 0x46, 0x2d]);
      await page.onFileSelected({ target: { files: [new File([content], 'o.pdf', { type: 'application/pdf' })], value: '' } } as unknown as Event);
      page.consent.set(true);
      page.submit();
      http.expectOne('/api/v1/prescriptions').flush(null, { status: 503, statusText: 'Service Unavailable' });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="manual-entry-error"]')).not.toBeNull();
    });

    it('ne propose pas la saisie manuelle pour une simple erreur de fichier', async () => {
      const fixture = TestBed.createComponent(ScanPage);
      const page = fixture.componentInstance;
      fixture.detectChanges(); // ngOnInit sans jeton : pas de chargement des profils famille
      sessionStorage.setItem('access_token', 'patient');
      const content = new Uint8Array(1000);
      content.set([0x25, 0x50, 0x44, 0x46, 0x2d]);
      await page.onFileSelected({ target: { files: [new File([content], 'o.pdf', { type: 'application/pdf' })], value: '' } } as unknown as Event);
      page.consent.set(true);
      page.submit();
      http.expectOne('/api/v1/prescriptions').flush(null, { status: 413, statusText: 'Payload Too Large' });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="manual-entry-error"]')).toBeNull();
    });
  });

  describe('Page Vérification', () => {
    it('permet une saisie entièrement manuelle dans le catalogue (RG02)', async () => {
      const fixture = TestBed.createComponent(VerificationPage);
      fixture.detectChanges();
      const html = fixture.nativeElement as HTMLElement;
      expect(html.textContent).toContain('Saisir mes médicaments à la main');

      const input = html.querySelector('#ajout-recherche') as HTMLInputElement;
      input.value = 'doli';
      input.dispatchEvent(new Event('input'));
      http.expectOne(r => r.url === '/api/medications/search' && r.params.get('q') === 'doli').flush([
        { code: DOLIPRANE.id, nom: 'DOLIPRANE', dosageBrut: '1', uniteDosage1: 'G', forme: 'COMPRIME SECABLE', presentation: '1 BOITE 10 COMPRIME' },
      ]);
      fixture.detectChanges();

      const choix = Array.from(html.querySelectorAll('.ajout .choix button')) as HTMLButtonElement[];
      expect(choix).toHaveLength(1);
      choix[0].click();
      fixture.detectChanges();

      expect(ordonnance.lignes()).toHaveLength(1);
      expect(ordonnance.lignes()[0].choix?.id).toBe(DOLIPRANE.id);
      expect(html.textContent).toContain('Ajouté à la main');
      expect(html.textContent).not.toContain('Confiance de la lecture');
      expect(ordonnance.peutContinuer()).toBe(true);
    });

    it('permet d’ajouter un médicament oublié par l’IA à côté des lignes lues', () => {
      ordonnance.charger([{ id: 1, texteLu: 'SPASFON 80 mg', score: 0.95, suggestions: [{ id: '6118010000010', nom: 'SPASFON 80 MG' }] }]);
      const fixture = TestBed.createComponent(VerificationPage);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Un médicament manque ? Ajoutez-le à la main');
    });

    it('signale quand aucun produit du catalogue ne correspond', () => {
      const fixture = TestBed.createComponent(VerificationPage);
      fixture.detectChanges();
      const input = fixture.nativeElement.querySelector('#ajout-recherche') as HTMLInputElement;
      input.value = 'zzz';
      input.dispatchEvent(new Event('input'));
      http.expectOne(r => r.url === '/api/medications/search').flush([]);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Aucun médicament du catalogue ne correspond');
      expect(ordonnance.lignes()).toHaveLength(0);
    });
  });
});
