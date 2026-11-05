import { Injectable, computed, signal } from '@angular/core';

export interface Produit {
  id: string;
  nom: string;
}

export interface LigneOrdonnance {
  id: number;
  texteLu: string;
  score: number;
  suggestions: Produit[];
  choix: Produit | null;
  supprimee: boolean;
}

export type Niveau = 'vert' | 'orange' | 'rouge';
export type Etat = 'confirmee' | 'a-verifier' | 'non-lue' | 'supprimee';

// Seuils du cahier des charges (section 5.1)
export const SEUIL_VERT = 0.85;
export const SEUIL_ORANGE = 0.6;

export function niveauDe(score: number): Niveau {
  if (score >= SEUIL_VERT) return 'vert';
  if (score >= SEUIL_ORANGE) return 'orange';
  return 'rouge';
}

export function etatDe(ligne: LigneOrdonnance): Etat {
  if (ligne.supprimee) return 'supprimee';
  if (ligne.choix) return 'confirmee';
  return niveauDe(ligne.score) === 'rouge' ? 'non-lue' : 'a-verifier';
}

function normaliser(texte: string): string {
  return texte.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

// Données fictives pour la démonstration.
// À remplacer par l'API du catalogue (US05) et par la réponse de la lecture IA (US07).
const CATALOGUE: Produit[] = [
  { id: 'p1', nom: 'DOLIPRANE 1000 MG, comprimé, boîte de 8' },
  { id: 'p2', nom: 'DOLIPRANE 500 MG, comprimé, boîte de 16' },
  { id: 'p3', nom: 'AUGMENTIN 1 G/125 MG, poudre en sachet' },
  { id: 'p4', nom: 'AUGMENTIN 500 MG/62,5 MG, poudre en sachet' },
  { id: 'p5', nom: 'AUGMENTIN 100 MG/12,5 MG/ML, suspension buvable' },
  { id: 'p6', nom: 'AMOXICILLINE 500 MG, gélule' },
  { id: 'p7', nom: 'IBUPROFENE 400 MG, comprimé' },
  { id: 'p8', nom: 'SPASFON, comprimé' },
];

type LigneLue = Omit<LigneOrdonnance, 'choix' | 'supprimee'>;

const LECTURE_DEMO: LigneLue[] = [
  { id: 1, texteLu: 'Doliprane 1g cp', score: 0.94, suggestions: [CATALOGUE[0]] },
  {
    id: 2,
    texteLu: 'Augmentin 1g sachet',
    score: 0.78,
    suggestions: [CATALOGUE[2], CATALOGUE[3], CATALOGUE[4]],
  },
  { id: 3, texteLu: '(écriture illisible)', score: 0.31, suggestions: [] },
];

@Injectable({ providedIn: 'root' })
export class OrdonnanceService {
  readonly lignes = signal<LigneOrdonnance[]>([]);

  // RG01 : toutes les lignes restantes doivent être confirmées
  readonly peutContinuer = computed(() => {
    const actives = this.lignes().filter((l) => !l.supprimee);
    return actives.length > 0 && actives.every((l) => l.choix !== null);
  });

  charger(): void {
    this.lignes.set(
      LECTURE_DEMO.map((l) => ({
        ...l,
        // une ligne verte est confirmée d'office, le patient peut encore la corriger
        choix: niveauDe(l.score) === 'vert' ? (l.suggestions[0] ?? null) : null,
        supprimee: false,
      })),
    );
  }

  choisir(id: number, produit: Produit): void {
    this.modifier(id, { choix: produit });
  }

  supprimer(id: number): void {
    this.modifier(id, { supprimee: true });
  }

  retablir(id: number): void {
    this.modifier(id, { supprimee: false });
  }

  // RG02 : seuls les produits du catalogue sont proposés
  rechercher(terme: string): Produit[] {
    const t = normaliser(terme);
    if (t.length < 2) return [];
    return CATALOGUE.filter((p) => normaliser(p.nom).includes(t)).slice(0, 5);
  }

  private modifier(id: number, changement: Partial<LigneOrdonnance>): void {
    this.lignes.update((lignes) =>
      lignes.map((l) => (l.id === id ? { ...l, ...changement } : l)),
    );
  }
}
