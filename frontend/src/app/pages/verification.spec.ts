import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { VerificationPage } from './verification';
import { OrdonnanceService } from '../services/ordonnance';

describe('VerificationPage (US08)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VerificationPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('n’affiche aucune donnée fictive quand rien n’a été lu', async () => {
    const fixture = TestBed.createComponent(VerificationPage);
    await fixture.whenStable();
    const texte = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(TestBed.inject(OrdonnanceService).lignes().length).toBe(0);
    expect(texte).toContain('Aucune ordonnance à vérifier');
    expect(texte).not.toContain('Doliprane');
  });

  it('affiche les lignes réellement lues par l’IA', async () => {
    TestBed.inject(OrdonnanceService).charger([
      { id: 1, texteLu: 'SPASFON 80 mg', score: 0.9, suggestions: [{ id: '611', nom: 'SPASFON 80 MG, COMPRIME' }] },
    ]);
    const fixture = TestBed.createComponent(VerificationPage);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('SPASFON 80 mg');
  });

  it('cherche dans le vrai catalogue lors d’une correction (RG02)', () => {
    TestBed.inject(OrdonnanceService).charger([{ id: 1, texteLu: '???', score: 0.3, suggestions: [] }]);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const page = TestBed.createComponent(VerificationPage).componentInstance as any;
    page.setTerme(1, { target: { value: 'augm' } } as unknown as Event);
    http.expectOne(r => r.url === '/api/medications/search' && r.params.get('q') === 'augm').flush([
      { code: '6118000160212', nom: 'AUGMENTIN', dosageBrut: '1 G', uniteDosage1: null, forme: 'COMPRIME', presentation: '1 BOITE 12' },
    ]);
    expect(page.resultats(1)[0]).toEqual({ id: '6118000160212', nom: 'AUGMENTIN 1 G, COMPRIME, 1 BOITE 12' });
  });
});
