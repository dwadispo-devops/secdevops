import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MedicationsPage } from './medications';

describe('MedicationsPage (US05)', () => {
  let http: HttpTestingController;
  let page: MedicationsPage;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MedicationsPage], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    page = TestBed.createComponent(MedicationsPage).componentInstance;
  });

  afterEach(() => http.verify());

  it('n’appelle pas le serveur avec moins de 2 caractères', () => {
    let result: unknown[] | undefined;
    page.search('d').subscribe(r => result = r);
    expect(result).toEqual([]);
  });

  it('interroge le catalogue avec le texte saisi', () => {
    let result: unknown[] = [];
    page.search('doli').subscribe(r => result = r);
    const req = http.expectOne(r => r.url === '/api/medications/search' && r.params.get('q') === 'doli');
    req.flush([{ code: '1', nom: 'DOLIPRANE', dosageBrut: '1000', uniteDosage1: 'MG' }]);
    expect(result.length).toBe(1);
    expect(page.searched()).toBe(true);
  });

  it('affiche un message si le catalogue est indisponible', () => {
    page.search('doli').subscribe();
    http.expectOne(() => true).flush({}, { status: 503, statusText: 'Unavailable' });
    expect(page.error()).toBe('La recherche est indisponible pour le moment.');
  });
});
