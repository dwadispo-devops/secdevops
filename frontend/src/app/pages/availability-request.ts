import { Component, inject, signal } from '@angular/core';
import { OrdonnanceService } from '../services/ordonnance';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface Medication {
  name: string;
  quantity: number;
}

@Component({
  selector: 'app-availability-request',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <main class="container">
      <h1>Demande de disponibilité</h1>
      <p>Recherchez vos médicaments dans les pharmacies proches.</p>

      <form (ngSubmit)="submit()">
        <h2>Médicaments</h2>

        @for (med of medications; track $index) {
          <div class="medication">
            <input
              aria-label="Nom du médicament"
              name="name-{{ $index }}"
              [(ngModel)]="med.name"
              placeholder="Nom du médicament"
              maxlength="200"
              required
            />

            <input
              aria-label="Quantité"
              type="number"
              name="quantity-{{ $index }}"
              [(ngModel)]="med.quantity"
              min="1"
              required
            />

            <button type="button" (click)="removeMedication($index)"
              [disabled]="medications.length === 1">
              Supprimer
            </button>
          </div>
        }

        <button type="button" (click)="addMedication()">
          + Ajouter un médicament
        </button>

        <h2>Localisation</h2>

        <button type="button" (click)="getLocation()">
          Utiliser ma position GPS
        </button>

        <p>Latitude : {{ latitude() ?? 'Non définie' }}</p>
        <p>Longitude : {{ longitude() ?? 'Non définie' }}</p>

        <label for="radius">Rayon de recherche (mètres)</label>
        <input
          id="radius"
          type="number"
          name="radius"
          [(ngModel)]="radiusM"
          min="100"
          max="20000"
          required
        />

        <label for="prescription-id">Référence de l’ordonnance à partager (facultatif)</label>
        <input id="prescription-id" name="prescriptionId" [(ngModel)]="prescriptionId" placeholder="Identifiant obtenu après le scan" />
        <p>En renseignant cette référence, vous partagez l’image et la lecture IA existante avec les pharmacies destinataires.</p>
        <div class="actions">
          <button type="submit" [disabled]="loading()">
            {{ loading() ? 'Envoi en cours...' : 'Envoyer ma demande' }}
          </button>
        </div>
      </form>

      @if (message()) {
        <p role="status">{{ message() }}</p>
      }
    </main>
  `,
  styles: [`
    .container { max-width: 760px; margin: 40px auto; padding: 24px; }
    h1 { color: #176b55; }
    h2 { margin-top: 28px; }
    .medication { display: flex; gap: 10px; margin: 12px 0; }
    input { padding: 10px; max-width: 100%; }
    button { padding: 10px 16px; cursor: pointer; }
    .actions { margin-top: 24px; }
    @media (max-width: 600px) {
      .medication { flex-direction: column; }
    }
  `]
})
export class AvailabilityRequestPage {
  private readonly http = inject(HttpClient);
  private readonly ordonnance = inject(OrdonnanceService);

  // US09 : reprend les médicaments confirmés à l'écran de vérification (US08)
  medications: Medication[] = this.depuisOrdonnance();
  radiusM = 5000;
  prescriptionId = sessionStorage.getItem('last_prescription_id') ?? '';

  latitude = signal<number | null>(null);
  longitude = signal<number | null>(null);
  loading = signal(false);
  message = signal('');

  private depuisOrdonnance(): Medication[] {
    const confirmes = this.ordonnance.lignes()
      .filter(l => !l.supprimee && l.choix)
      .map(l => ({ name: l.choix!.nom, quantity: 1 }));
    return confirmes.length ? confirmes : [{ name: '', quantity: 1 }];
  }

  addMedication(): void {
    this.medications.push({ name: '', quantity: 1 });
  }

  removeMedication(index: number): void {
    if (this.medications.length > 1) {
      this.medications.splice(index, 1);
    }
  }

  getLocation(): void {
    if (!navigator.geolocation) {
      this.message.set('La géolocalisation est indisponible.');
      return;
    }

    navigator.geolocation.getCurrentPosition(
      position => {
        this.latitude.set(position.coords.latitude);
        this.longitude.set(position.coords.longitude);
        this.message.set('Position GPS récupérée.');
      },
      () => this.message.set('Impossible de récupérer votre position.')
    );
  }

  submit(): void {
    if (this.loading()) return;
    if (this.prescriptionId && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(this.prescriptionId.trim())) {
      this.message.set('Référence d’ordonnance invalide.'); return;
    }

    if (this.latitude() === null || this.longitude() === null) {
      this.message.set('Veuillez autoriser votre position GPS.');
      return;
    }

    const medications = this.medications.map(m => ({
      name: m.name.trim(),
      quantity: Number(m.quantity)
    }));

    if (
      !Number.isFinite(this.latitude()) || !Number.isFinite(this.longitude()) ||
      this.latitude()! < -90 || this.latitude()! > 90 ||
      this.longitude()! < -180 || this.longitude()! > 180 ||
      medications.length === 0 ||
      medications.some(m => !m.name || m.name.length > 200 ||
        !Number.isInteger(m.quantity) || m.quantity < 1) ||
      !Number.isInteger(this.radiusM) ||
      this.radiusM < 100 || this.radiusM > 20000
    ) {
      this.message.set('Vérifiez les médicaments, les quantités et le rayon.');
      return;
    }

    const token = sessionStorage.getItem('access_token');

    if (!token) {
      this.message.set('Connectez-vous avec un compte patient.');
      return;
    }

    this.loading.set(true);
    this.message.set('');

    this.http.post<{ id?: string }>(
      '/api/availability-requests',
      {
        latitude: this.latitude(),
        longitude: this.longitude(),
        radiusM: this.radiusM,
        medications
      },
      { headers: { Authorization: `Bearer ${token}` } }
    ).subscribe({
      next: created => {
        if (this.prescriptionId && created.id) {
          this.http.put(`/api/availability-requests/${created.id}/prescription`,
            { prescriptionId: this.prescriptionId.trim() }, { headers: { Authorization: `Bearer ${token}` } }).subscribe({
              next: () => { this.loading.set(false); this.message.set('Demande envoyée avec son ordonnance.'); },
              error: () => { this.loading.set(false); this.message.set(`Demande créée (${created.id}), mais ordonnance non associée. Vérifiez la référence et vos droits. Ne renvoyez pas la demande.`); },
            });
          return;
        }
        this.loading.set(false);
        this.message.set('Votre demande a été envoyée avec succès !');
      },
      error: error => {
        this.loading.set(false);
        this.message.set(
          error.status === 401
            ? 'Session invalide ou expirée. Reconnectez-vous.'
            : 'Échec de l’envoi. Vérifiez la connexion au serveur.'
        );
      }
    });
  }
}
