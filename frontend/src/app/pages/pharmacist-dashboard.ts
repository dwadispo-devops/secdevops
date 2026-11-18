import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PharmacistDashboardService, PharmacistDashboard } from '../services/pharmacist-dashboard.service';

@Component({
  selector: 'app-pharmacist-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './pharmacist-dashboard.html',
  styleUrl: './pharmacist-dashboard.scss',
})
export class PharmacistDashboardPage implements OnInit {
  private readonly api = inject(PharmacistDashboardService);

  readonly dashboard = signal<PharmacistDashboard | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');

  ngOnInit() {
    this.reload();
  }

  reload() {
    this.error.set('');

    if (!sessionStorage.getItem('access_token')) {
      this.error.set('Connectez-vous avec un compte pharmacien.');
      return;
    }

    this.loading.set(true);

    this.api.load().subscribe({
      next: data => {
        this.dashboard.set(data);
        this.loading.set(false);
      },
      error: e => {
        this.error.set(e.status === 401
          ? 'Session invalide ou expirée. Reconnectez-vous.'
          : e.status === 403
            ? 'Accès réservé aux pharmaciens.'
            : 'Impossible de charger le tableau de bord. Réessayez.');
        this.loading.set(false);
      },
    });
  }

  label(status: string) {
    return ({
      ACTIVE: 'Active',
      RETIREE: 'Retirée',
      ANNULEE: 'Annulée',
      EXPIREE: 'Expirée',
    } as Record<string, string>)[status] ?? status;
  }
}
