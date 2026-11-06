import { Component, inject, signal, effect } from '@angular/core';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { Pharmacy, PharmacyService } from '../services/pharmacy.service';
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
        @if (apercu(); as liste) {
          <div class="apercu" role="status" [class.vide]="liste.length === 0">
            @if (liste.length === 0) {
              <p>⚠️ Aucune pharmacie dans un rayon de {{ radiusM / 1000 }} km. Élargissez le rayon pour que votre demande soit reçue.</p>
            } @else {
              <p><strong>📍 {{ liste.length >= 50 ? 'Au moins 50' : liste.length }} pharmacie(s)</strong>
                 recevront votre demande (rayon de {{ radiusM / 1000 }} km) :</p>
              <ul>
                @for (p of liste.slice(0, 5); track p.id) { <li>{{ p.name }} – {{ distanceLisible(p.distanceM) }}</li> }
              </ul>
              @if (liste.length > 5) { <p class="plus">… et {{ liste.length - 5 }} autre(s).</p> }
            }
          </div>
        }

        <label for="radius">Rayon de recherche (mètres)</label>
        <input
          id="radius"
          type="number"
          name="radius"
          [(ngModel)]="radiusM" (ngModelChange)="onRayonChange()"
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
    .apercu { margin: 12px 0; padding: 12px 16px; border-radius: 8px; background: #e8f5ef; border-left: 4px solid #176b55; }
    .apercu.vide { background: #fdecea; border-left-color: #b00020; color: #8a1020; }
    .apercu p { margin: 0 0 6px; } .apercu ul { margin: 0; padding-left: 20px; } .apercu .plus { margin-top: 6px; color: #555; }
    @media (max-width: 600px) {
      .medication { flex-direction: column; }
    }
  `]
})
export class AvailabilityRequestPage {
  private readonly http = inject(HttpClient);
  private readonly ordonnance = inject(OrdonnanceService);
  private readonly pharmacies = inject(PharmacyService);
  private readonly routeurSuivi = inject(Router);
  private readonly apercuDemande = new Subject<void>();

  /** Pharmacies qui recevront la demande (API US04). null = position encore inconnue. */
  readonly apercu = signal<Pharmacy[] | null>(null);

  private readonly suiviPosition = effect(() => {
    if (this.latitude() !== null && this.longitude() !== null) this.apercuDemande.next();
  });

  private readonly abonnementApercu = this.apercuDemande.pipe(
    debounceTime(400),
    switchMap(() => this.chargerApercu()),
    takeUntilDestroyed(),
  ).subscribe(liste => this.apercu.set(liste));

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
      .map(l => ({ name: l.choix!.nom, quantity: l.quantite ?? 1 }));
    return confirmes.length ? confirmes : [{ name: '', quantity: 1 }];
  }

  /** Recherche les pharmacies dans le rayon choisi ; rien si la position ou le rayon sont invalides. */
  chargerApercu(): Observable<Pharmacy[] | null> {
    const lat = this.latitude(), lng = this.longitude(), rayon = Number(this.radiusM);
    if (lat === null || lng === null || !Number.isInteger(rayon) || rayon < 100 || rayon > 20000) return of(null);
    return this.pharmacies.getNearbyPharmacies(lat, lng, rayon).pipe(catchError(() => of(null)));
  }

  /** Après l'envoi : mémorise la demande (position, rayon) puis ouvre la page de suivi. */
  private allerAuSuivi(id: string | undefined): void {
    if (!id) return;
    sessionStorage.setItem('demande_suivie', JSON.stringify({
      id, latitude: this.latitude(), longitude: this.longitude(), radiusM: Number(this.radiusM) }));
    this.routeurSuivi.navigateByUrl(`/suivi-demande/${id}`).catch(() => undefined);
  }

  onRayonChange(): void {
    this.apercuDemande.next();
  }

  distanceLisible(metres: number): string {
    return metres < 1000 ? `${Math.round(metres)} m` : `${(metres / 1000).toFixed(1).replace('.', ',')} km`;
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
              next: () => { this.loading.set(false); this.message.set('Demande envoyée avec son ordonnance.'); this.allerAuSuivi(created.id); },
              error: () => { this.loading.set(false); this.message.set(`Demande créée (${created.id}), mais ordonnance non associée. Vérifiez la référence et vos droits. Ne renvoyez pas la demande.`); },
            });
          return;
        }
        this.loading.set(false);
        this.message.set('Votre demande a été envoyée avec succès !');
        this.allerAuSuivi(created.id);
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
