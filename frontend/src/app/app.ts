import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { NotificationBellComponent } from './components/notification-bell';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBellComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  protected readonly title = signal('frontend');
  protected readonly connected = signal(!!sessionStorage.getItem('access_token'));

  constructor() {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.connected.set(!!sessionStorage.getItem('access_token')));
  }

  protected logout() {
    for (const key of ['access_token', 'refresh_token', 'last_prescription_id']) sessionStorage.removeItem(key);
    this.connected.set(false);
    void this.router.navigateByUrl('/');
  }
}
