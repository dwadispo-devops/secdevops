import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { DutyPharmacy, PharmacyService } from '../services/pharmacy.service';

@Component({
  selector: 'app-pharmacies-garde',
  standalone: true,
  template: `
    <main class="garde-page">
      <section class="page-heading">
        <div>
          <span class="eyebrow">DwaDispo · Gardes</span>
          <h1>Pharmacies de garde</h1>
          @if (dateLabel()) {
            <p class="date-label">{{ dateLabel() }}</p>
          }
        </div>

        <button
          class="location-button"
          type="button"
          (click)="locateMe()"
          [disabled]="locating()"
        >
          <span>⌖</span>
          {{ locating() ? 'Localisation...' : 'Trier par distance' }}
        </button>
      </section>

      @if (locationNote()) {
        <p class="note">{{ locationNote() }}</p>
      }

      <section class="garde-list" aria-live="polite">
        @if (loading()) {
          <div class="message-state">
            <span class="loader"></span>
            <p>Recherche des pharmacies de garde...</p>
          </div>
        } @else if (errorMessage()) {
          <div class="message-state error-state">
            <span>!</span>
            <p>{{ errorMessage() }}</p>
            <button type="button" (click)="load()">Réessayer</button>
          </div>
        } @else if (pharmacies().length === 0) {
          <div class="message-state">
            <span>✚</span>
            <p>Aucune pharmacie de garde n’est enregistrée pour aujourd’hui.</p>
          </div>
        } @else {
          @for (pharmacy of pharmacies(); track pharmacy.id) {
            <article class="garde-card">
              <div class="garde-info">
                <strong>{{ pharmacy.name }}</strong>
                <small>{{ pharmacy.address || 'Adresse non renseignée' }}</small>
                @if (pharmacy.openingHours) {
                  <small>{{ pharmacy.openingHours }}</small>
                }
                <small class="until">De garde jusqu’au {{ formatUntil(pharmacy.dutyUntil) }}</small>
                @if (pharmacy.distanceM !== null) {
                  <small class="distance">{{ formatDistance(pharmacy.distanceM) }} de votre position</small>
                }
              </div>

              <div class="garde-actions">
                @if (pharmacy.phone) {
                  <a class="action" [href]="'tel:' + cleanPhone(pharmacy.phone)">Appeler</a>
                }
                <a
                  class="action secondary"
                  [href]="directionsUrl(pharmacy)"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Itinéraire
                </a>
              </div>
            </article>
          }
        }
      </section>
    </main>
  `,
  styleUrl: './pharmacies-garde.scss',
})
export class PharmaciesGardePage implements OnInit {
  private readonly pharmacyService = inject(PharmacyService);

  readonly pharmacies = signal<DutyPharmacy[]>([]);
  readonly date = signal<string | null>(null);
  readonly loading = signal(false);
  readonly locating = signal(false);
  readonly errorMessage = signal('');
  readonly locationNote = signal('');

  private coords: { lat: number; lng: number } | null = null;

  readonly dateLabel = computed(() => {
    const iso = this.date();
    if (!iso) return '';
    const [y, m, d] = iso.split('-').map(Number);
    const label = new Date(y, m - 1, d).toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
    return label.charAt(0).toUpperCase() + label.slice(1);
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.pharmacyService
      .getOnDutyPharmacies(this.coords?.lat, this.coords?.lng)
      .subscribe({
        next: (response) => {
          this.date.set(response.date);
          this.pharmacies.set(response.pharmacies);
          this.loading.set(false);
        },
        error: (error) => {
          console.error('Erreur de récupération des pharmacies de garde :', error);
          this.errorMessage.set(
            'Impossible de récupérer les pharmacies de garde. Vérifiez la connexion au serveur.',
          );
          this.loading.set(false);
        },
      });
  }

  locateMe(): void {
    if (!navigator.geolocation) {
      this.locationNote.set('La géolocalisation n’est pas disponible sur ce navigateur.');
      return;
    }

    this.locating.set(true);
    this.locationNote.set('');

    navigator.geolocation.getCurrentPosition(
      (position) => {
        this.coords = { lat: position.coords.latitude, lng: position.coords.longitude };
        this.locating.set(false);
        this.load();
      },
      () => {
        this.locating.set(false);
        this.locationNote.set(
          'Position indisponible : la liste reste triée par nom. Autorisez la localisation pour la trier par distance.',
        );
      },
      { enableHighAccuracy: false, timeout: 15000, maximumAge: 60000 },
    );
  }

  formatDistance(distance: number): string {
    return distance < 1000
      ? `${Math.round(distance)} m`
      : `${(distance / 1000).toFixed(1)} km`;
  }

  formatUntil(iso: string): string {
    const [, m, d] = iso.split('-');
    return `${d}/${m}`;
  }

  cleanPhone(phone: string): string {
    return phone.replace(/[^\d+]/g, '');
  }

  directionsUrl(pharmacy: DutyPharmacy): string {
    return `https://www.google.com/maps/dir/?api=1&destination=${pharmacy.latitude},${pharmacy.longitude}`;
  }
}
