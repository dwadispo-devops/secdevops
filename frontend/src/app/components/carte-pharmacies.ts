import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, effect, input } from '@angular/core';
import * as L from 'leaflet';

export interface PointCarte { id: string; lat: number; lng: number; couleur: string; titre: string; detail: string; }

/** Carte Leaflet réutilisable (même rendu que la page /pharmacies, US04). */
@Component({
  selector: 'app-carte-pharmacies',
  template: `<div #conteneur class="carte" role="img" aria-label="Carte des pharmacies"></div>`,
  styles: [`.carte { height: 420px; width: 100%; border-radius: 12px; overflow: hidden; border: 1px solid var(--couleur-bordure); }`],
})
export class CartePharmaciesComponent implements AfterViewInit, OnDestroy {
  readonly points = input<PointCarte[]>([]);
  readonly centre = input<{ lat: number; lng: number } | null>(null);

  @ViewChild('conteneur') private conteneur!: ElementRef<HTMLDivElement>;
  private map?: L.Map;
  private couche?: L.LayerGroup;
  private position?: L.CircleMarker;

  constructor() {
    effect(() => this.dessiner(this.points(), this.centre()));
  }

  ngAfterViewInit(): void {
    try {
      const c = this.centre() ?? { lat: 31.6258, lng: -7.9891 };
      this.map = L.map(this.conteneur.nativeElement, { center: [c.lat, c.lng], zoom: 14 });
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors', maxZoom: 19,
      }).addTo(this.map);
      this.couche = L.layerGroup().addTo(this.map);
    } catch {
      return; // environnement sans rendu (tests)
    }
    this.dessiner(this.points(), this.centre());
  }

  private dessiner(points: PointCarte[], centre: { lat: number; lng: number } | null): void {
    if (!this.map || !this.couche) return;
    this.couche.clearLayers();
    for (const p of points) {
      const popup = document.createElement('div');
      const titre = document.createElement('strong');
      const detail = document.createElement('p');
      titre.textContent = p.titre;      // textContent : pas d'injection HTML
      detail.textContent = p.detail;
      popup.append(titre, detail);
      L.circleMarker([p.lat, p.lng], { radius: 8, color: '#ffffff', weight: 2, fillColor: p.couleur, fillOpacity: 1 })
        .bindPopup(popup).addTo(this.couche);
    }
    this.position?.remove();
    if (centre) {
      this.position = L.circleMarker([centre.lat, centre.lng], { radius: 9, color: '#232323', weight: 4, fillColor: '#faf6ee', fillOpacity: 1 })
        .bindPopup('Votre position').addTo(this.map);
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
