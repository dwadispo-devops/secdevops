import { TestBed } from '@angular/core/testing';
import { niveauDe, OrdonnanceService } from './ordonnance';

describe('niveauDe', () => {
  it('classe les scores selon les seuils du cahier des charges', () => {
    expect(niveauDe(0.94)).toBe('vert');
    expect(niveauDe(0.85)).toBe('vert');
    expect(niveauDe(0.78)).toBe('orange');
    expect(niveauDe(0.6)).toBe('orange');
    expect(niveauDe(0.31)).toBe('rouge');
  });
});

describe('OrdonnanceService', () => {
  let service: OrdonnanceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OrdonnanceService);
    service.charger();
  });

  it('confirme automatiquement les lignes reconnues (vert)', () => {
    const [doliprane] = service.lignes();
    expect(doliprane.choix).not.toBeNull();
  });

  it('bloque Continuer tant que des lignes ne sont pas traitées (RG01)', () => {
    expect(service.peutContinuer()).toBe(false);
  });

  it('débloque Continuer une fois les lignes confirmées ou supprimées (RG01)', () => {
    const [, augmentin, illisible] = service.lignes();
    service.choisir(augmentin.id, augmentin.suggestions[0]);
    expect(service.peutContinuer()).toBe(false);
    service.supprimer(illisible.id);
    expect(service.peutContinuer()).toBe(true);
  });

  it('ne propose que des produits du catalogue (RG02)', () => {
    expect(service.rechercher('augmentin').length).toBeGreaterThan(0);
    expect(service.rechercher('produit-invente-xyz')).toEqual([]);
  });

  it('ignore les recherches trop courtes', () => {
    expect(service.rechercher('a')).toEqual([]);
  });

  it('conserve une quantité absente et réinitialise sa confirmation après correction (US17)', () => {
    service.charger([{ id: 1, texteLu: 'Produit', score: 1, suggestions: [{ id: '6118000000001', nom: 'Produit' }], quantite: null }]);
    expect(service.lignes()[0].quantite).toBeNull();
    service.quantifier(1, 3);
    service.confirmerQuantite(1, true);
    expect(service.lignes()[0].quantiteConfirmee).toBe(true);
    service.quantifier(1, 4);
    expect(service.lignes()[0].quantiteConfirmee).toBe(false);
    service.confirmerQuantite(1, true);
    service.choisir(1, { id: '6118000000002', nom: 'Autre conditionnement' });
    expect(service.lignes()[0].quantiteConfirmee).toBe(false);
    expect(service.lignes()[0].quantite).toBe(4);
  });
});
