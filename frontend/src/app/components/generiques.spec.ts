import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GeneriquesComponent } from './generiques';
import { ReponseGeneriques } from '../services/generiques.service';

describe('US16 — génériques moins chers', () => {
  const AUGMENTIN = '6118000160182';
  const url = `/api/medications/${AUGMENTIN}/generiques?limit=5`;
  let fixture: ComponentFixture<GeneriquesComponent>;
  let http: HttpTestingController;

  const reponse: ReponseGeneriques = {
    reference: { code: AUGMENTIN, nom: 'AUGMENTIN', dci: 'AMOXICILLINE / ACIDE CLAVULANIQUE', dosage: '1 / 125 G / MG',
      forme: 'POUDRE POUR SUSPENSION BUVABLE', presentation: '1 BOITE 16 SACHET', ppv: 168.2, prixUnitaire: 10.51,
      tauxRemboursement: 70, princepsGenerique: 'P' },
    generiques: [
      { code: '6118000041177', nom: 'ZAMOX', dosage: '1 / 125 G / MG', forme: 'POUDRE POUR SUSPENSION BUVABLE',
        presentation: '1 BOITE 24 SACHET', ppv: 167, prixUnitaire: 6.96, economiePourcent: 33, comparaison: 'PAR_UNITE',
        tauxRemboursement: 70, moinsRembourse: false },
      { code: '6118000220459', nom: 'CURAM 1G/125MG', dosage: '1 / 125 G / MG', forme: 'POUDRE POUR SUSPENSION BUVABLE',
        presentation: '1 BOITE 16 SACHET', ppv: 116, prixUnitaire: 7.25, economiePourcent: 31, comparaison: 'PAR_UNITE',
        tauxRemboursement: 0, moinsRembourse: true },
    ],
    message: null,
    source: 'Catalogue CNOPS 2014 – prix publics indicatifs',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [GeneriquesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GeneriquesComponent);
    fixture.componentRef.setInput('code', AUGMENTIN);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });

  const texte = () => fixture.nativeElement.textContent as string;
  const ouvrir = () => {
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  it('ne charge rien tant que le patient ne le demande pas', () => {
    http.expectNone(url);
    expect(texte()).toContain('Voir les génériques moins chers');
  });

  it('affiche les génériques, l’économie et le signalement « moins bien remboursé »', () => {
    ouvrir();
    http.expectOne(url).flush(reponse);
    fixture.detectChanges();
    expect(texte()).toContain('ZAMOX');
    expect(texte()).toContain('−33 %');
    expect(texte()).toContain('6.96 MAD par unité');
    expect(texte()).toContain('Moins bien remboursé');
    expect(texte()).toContain('Catalogue CNOPS 2014');
    expect(texte()).toContain('pharmacien');
  });

  it('affiche le message quand aucun générique n’est moins cher', () => {
    ouvrir();
    http.expectOne(url).flush({ ...reponse, generiques: [], message: 'Aucun générique moins cher.' });
    fixture.detectChanges();
    expect(texte()).toContain('Aucun générique moins cher.');
  });

  it('ne recharge pas à chaque ouverture', () => {
    ouvrir();
    http.expectOne(url).flush(reponse);
    ouvrir();
    ouvrir();
    http.expectNone(url);
    expect(texte()).toContain('ZAMOX');
  });

  it('affiche une erreur compréhensible si le service est indisponible', () => {
    ouvrir();
    http.expectOne(url).flush(null, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(texte()).toContain('indisponibles');
  });

  it('ne s’affiche pas pour un produit hors catalogue (données de démonstration)', () => {
    fixture.componentRef.setInput('code', 'p1');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});
