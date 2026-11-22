import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NotificationBellComponent } from './components/notification-bell';
import { AuthService } from './services/auth.service';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBellComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly connected = this.auth.connected;
  protected readonly profile = this.auth.profile;

  constructor() {
    if (sessionStorage.getItem('access_token')) {
      this.auth.loadProfile().subscribe({
        error: () => this.auth.clearSession(),
      });
    }
  }

  protected logout() {
    this.auth.clearSession();
    void this.router.navigateByUrl('/');
  }
}
