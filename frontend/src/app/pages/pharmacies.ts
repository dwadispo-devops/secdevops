import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';

import {
  Pharmacy,
  PharmacyService,
} from '../services/pharmacy.service';

@Component({
  selector: 'app-pharmacies',
  standalone: true,
  imports: [CommonModule],
  template: `
    <main class="pharmacies-page">
      <section class="page-heading">
        <div>
          <span class="eyebrow">DwaDispo · Localisation</span>
          <h1>Trouvez une pharmacie près de vous</h1>
          <p>
            Explorez les pharmacies autour de votre position
            et trouvez facilement celle qui vous convient.
          </p>
        </div>

        <button
          class="location-button"
          (click)="locateMe()"
          type="button"
          [disabled]="locating"
        >
          <span>⌖</span>
          {{ locating ? 'Localisation...' : 'Me localiser' }}
        </button>
      </section>

      <section class="search-bar">
        <span class="search-icon">⌕</span>
        <input
          type="search"
          placeholder="Rechercher une pharmacie..."
          (input)="onSearchChange($event)"
          aria-label="Rechercher une pharmacie"
        />
        <span class="result-count">
          {{ filteredPharmacies.length }} pharmacie(s)
        </span>
      </section>

      <section class="map-layout">
        <aside class="pharmacy-panel">
          <div class="panel-heading">
            <div>
              <h2>Pharmacies proches</h2>
              <p>Les établissements autour de vous</p>
            </div>
            <span class="count-badge">{{ filteredPharmacies.length }}</span>
          </div>

          <div class="pharmacy-list">
            @if (loading) {
              <div class="message-state">
                <span class="loader"></span>
                <p>Recherche des pharmacies...</p>
              </div>
            } @else if (errorMessage) {
              <div class="message-state error-state">
                <span>!</span>
                <p>{{ errorMessage }}</p>
                <button type="button" (click)="loadPharmacies()">
                  Réessayer
                </button>
              </div>
            } @else if (filteredPharmacies.length === 0) {
              <div class="message-state">
                <span>⌕</span>
                <p>Aucune pharmacie trouvée.</p>
              </div>
            } @else {
              @for (pharmacy of filteredPharmacies; track pharmacy.id) {
                <button
                  type="button"
                  class="pharmacy-item"
                  [class.selected]="selectedPharmacy?.id === pharmacy.id"
                  (click)="selectPharmacy(pharmacy)"
                >
                  <span class="pharmacy-icon">✚</span>

                  <span class="pharmacy-info">
                    <strong>{{ pharmacy.name }}</strong>
                    <small>
                      {{ pharmacy.address || 'Adresse non renseignée' }}
                    </small>
                    <small class="distance">
                      {{ formatDistance(pharmacy.distanceM) }}
                      de votre position
                    </small>
                  </span>

                  <span class="item-arrow">›</span>
                </button>
              }
            }
          </div>
        </aside>

        <div class="map-panel">
          <div class="map-toolbar">
            <div>
              <span class="map-status"></span>
              <strong>Carte des pharmacies</strong>
            </div>
            <span class="map-subtitle">Marrakech et environs</span>
          </div>

          <div #mapContainer class="map-container"></div>

          <div class="map-legend">
            <span class="legend-dot"></span>
            Pharmacie
            <span class="legend-user"></span>
            Votre position
          </div>

          @if (locationAccuracy !== null) {
            <p class="location-accuracy">
              Précision de localisation :
              <strong>{{ formatDistance(locationAccuracy) }}</strong>
            </p>
          }
        </div>
      </section>
    </main>
  `,
  styleUrl: './pharmacies.scss',
})
export class PharmaciesPage implements AfterViewInit, OnDestroy {
  @ViewChild('mapContainer')
  private mapContainer!: ElementRef<HTMLDivElement>;

  private readonly pharmacyService = inject(PharmacyService);

  private map?: L.Map;
  private markersLayer?: L.LayerGroup;
  private readonly markers = new Map<string, L.CircleMarker>();

  pharmacies: Pharmacy[] = [];
  filteredPharmacies: Pharmacy[] = [];
  selectedPharmacy: Pharmacy | null = null;

  loading = false;
  locating = false;
  errorMessage = '';
  searchTerm = '';

  private latitude = 31.6258;
  private longitude = -7.9891;

  private userMarker?: L.CircleMarker;

  locationAccuracy: number | null = null;

  ngAfterViewInit(): void {
    this.initializeMap();
    this.loadPharmacies();
  }

  private initializeMap(): void {
    this.map = L.map(this.mapContainer.nativeElement, {
      center: [this.latitude, this.longitude],
      zoom: 14,
      zoomControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(this.map);

    this.markersLayer = L.layerGroup().addTo(this.map);
  }

  loadPharmacies(): void {
    this.loading = true;
    this.errorMessage = '';

    this.pharmacyService
      .getNearbyPharmacies(this.latitude, this.longitude, 5000)
      .subscribe({
        next: (data) => {
          this.pharmacies = data;
          this.applySearch();
          this.displayMarkers();
          this.loading = false;
        },
        error: (error) => {
          console.error(
            'Erreur de récupération des pharmacies :',
            error,
          );

          this.errorMessage =
            'Impossible de récupérer les pharmacies. Vérifiez la connexion au serveur.';

          this.loading = false;
        },
      });
  }

  private displayMarkers(): void {
    if (!this.markersLayer) return;

    this.markersLayer.clearLayers();
    this.markers.clear();

    for (const pharmacy of this.filteredPharmacies) {
      const marker = L.circleMarker(
        [pharmacy.latitude, pharmacy.longitude],
        {
          radius: 8,
          color: '#ffffff',
          weight: 2,
          fillColor: '#079779',
          fillOpacity: 1,
        },
      );

      const popup = document.createElement('div');
      const name = document.createElement('strong');
      const distance = document.createElement('p');

      name.textContent = pharmacy.name;
      distance.textContent = this.formatDistance(pharmacy.distanceM);

      popup.append(name, distance);
      marker.bindPopup(popup);

      marker.addTo(this.markersLayer);
      this.markers.set(pharmacy.id, marker);
    }
  }

  onSearchChange(event: Event): void {
    this.searchTerm = (event.target as HTMLInputElement).value;
    this.applySearch();
    this.displayMarkers();
  }

  private applySearch(): void {
    const term = this.searchTerm.trim().toLowerCase();

    this.filteredPharmacies = this.pharmacies.filter((pharmacy) =>
      pharmacy.name.toLowerCase().includes(term),
    );
  }

  selectPharmacy(pharmacy: Pharmacy): void {
    this.selectedPharmacy = pharmacy;

    this.map?.setView(
      [pharmacy.latitude, pharmacy.longitude],
      17,
      { animate: true },
    );

    this.markers.get(pharmacy.id)?.openPopup();
  }

  locateMe(): void {
    if (!navigator.geolocation) {
      this.errorMessage =
        'La géolocalisation n’est pas disponible sur ce navigateur.';
      return;
    }

    this.locating = true;
    this.errorMessage = '';

    navigator.geolocation.getCurrentPosition(
      (position) => {
        this.latitude = position.coords.latitude;
        this.longitude = position.coords.longitude;
        this.locationAccuracy = position.coords.accuracy;
        this.map?.setView(
          [this.latitude, this.longitude],
          16,
          { animate: true },
        );

        this.userMarker?.remove();

        if (this.map) {
          this.userMarker = L.circleMarker(
            [this.latitude, this.longitude],
            {
              radius: 9,
              color: '#ffffff',
              weight: 3,
              fillColor: '#2878e8',
              fillOpacity: 1,
            },
          )
            .bindPopup(
              `Votre position (précision : ${this.formatDistance(
                this.locationAccuracy,
              )})`,
            )
            .addTo(this.map);
        }

        this.locating = false;
        this.loadPharmacies();
      },

      (error) => {
        this.locating = false;

        switch (error.code) {
          case error.PERMISSION_DENIED:
            this.errorMessage =
              'Autorisation de localisation refusée. Autorisez-la dans votre navigateur.';
            break;

          case error.POSITION_UNAVAILABLE:
            this.errorMessage =
              'Votre position est actuellement indisponible.';
            break;

          case error.TIMEOUT:
            this.errorMessage =
              'La localisation prend trop de temps. Réessayez.';
            break;

          default:
            this.errorMessage =
              'Une erreur est survenue pendant la localisation.';
        }

        console.error('Erreur de géolocalisation :', error);
      },

      {
        enableHighAccuracy: true,
        timeout: 30000,
        maximumAge: 0,
      },
    );
  }

  formatDistance(distance: number): string {
    if (distance < 1000) {
      return `${Math.round(distance)} m`;
    }

    return `${(distance / 1000).toFixed(1)} km`;
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
