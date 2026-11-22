import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, timer } from 'rxjs';
import { AppNotification, NotificationsService } from '../services/notifications.service';

export const RAFRAICHISSEMENT_NOTIFS_MS = 30_000;

/** US14 / EF24 – Cloche du menu : nombre de notifications non lues et liste des dernières. */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe],
  template: `
    <div class="cloche">
      <button type="button" class="bouton" (click)="ouvert.set(!ouvert())" [attr.aria-expanded]="ouvert()"
              [attr.aria-label]="'Notifications : ' + nonLues() + ' non lue(s)'">
        🔔 @if (nonLues() > 0) { <span class="badge">{{ nonLues() > 9 ? '9+' : nonLues() }}</span> }
      </button>
      @if (ouvert()) {
        <div class="panneau" role="dialog" aria-label="Notifications">
          <div class="entete">
            <strong>Notifications</strong>
            @if (nonLues() > 0) { <button type="button" class="lien" (click)="toutLire()">Tout marquer comme lu</button> }
          </div>
          @for (n of items(); track n.id) {
            <button type="button" class="item" [class.nonlu]="!n.read" (click)="ouvrir(n)">
              <strong>{{ n.title }}</strong>
              <span>{{ n.message }}</span>
              <small>{{ n.createdAt | date: 'dd/MM HH:mm' }}</small>
            </button>
          } @empty {
            <p class="vide">Aucune notification pour le moment.</p>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .cloche { position: relative; }
    .bouton { position: relative; background: none; border: 0; font-size: 1.15rem; cursor: pointer; padding: .3rem .6rem; border-radius: 6px; }
    .bouton:hover { background: rgba(255,255,255,.18); }
    .badge { position: absolute; top: -2px; right: -2px; background: #dc2626; color: #fff; font-size: .7rem; font-weight: 700;
             min-width: 18px; height: 18px; border-radius: 9px; display: grid; place-items: center; padding: 0 4px; }
    .panneau { position: absolute; right: 0; top: 2.4rem; width: min(360px, 90vw); max-height: 70vh; overflow-y: auto; z-index: 2000;
               background: #fff; color: #1f2937; border-radius: 10px; box-shadow: 0 10px 30px rgba(0,0,0,.18); padding: .5rem; }
    .entete { display: flex; justify-content: space-between; align-items: center; padding: .4rem .5rem .6rem; }
    .lien { background: none; border: 0; color: var(--couleur-or-texte); cursor: pointer; font-size: .85rem; text-decoration: underline; }
    .item { display: grid; gap: .15rem; width: 100%; text-align: left; background: #fff; border: 0; border-radius: 8px;
            padding: .6rem .7rem; cursor: pointer; font: inherit; color: inherit; }
    .item:hover { background: #f6f1e4; } .item.nonlu { background: #f3ecdb; } .item.nonlu strong::before { content: '● '; color: #b89b5e; }
    .item span { font-size: .9rem; color: #374151; } .item small { color: #6b7280; font-size: .75rem; }
    .vide { color: #6b7280; padding: .6rem; margin: 0; }
  `],
})
export class NotificationBellComponent {
  private readonly service = inject(NotificationsService);
  private readonly router = inject(Router);
  readonly items = signal<AppNotification[]>([]);
  readonly nonLues = signal(0);
  readonly ouvert = signal(false);

  constructor() {
    timer(0, RAFRAICHISSEMENT_NOTIFS_MS)
      .pipe(switchMap(() => this.service.mes().pipe(catchError(() => of(null)))), takeUntilDestroyed())
      .subscribe(r => { if (r) { this.items.set(r.items); this.nonLues.set(r.unread); } });
  }

  ouvrir(n: AppNotification): void {
    this.ouvert.set(false);
    if (!n.read) {
      this.service.lire(n.id).subscribe({ error: () => undefined });
      this.items.update(l => l.map(x => (x.id === n.id ? { ...x, read: true } : x)));
      this.nonLues.update(c => Math.max(0, c - 1));
    }
    // Lien interne uniquement (pas de redirection vers un site externe).
    if (n.link && n.link.startsWith('/') && !n.link.startsWith('//')) {
      this.router.navigateByUrl(n.link).catch(() => undefined);
    }
  }

  toutLire(): void {
    this.service.toutLire().subscribe({ error: () => undefined });
    this.items.update(l => l.map(x => ({ ...x, read: true })));
    this.nonLues.set(0);
  }
}
