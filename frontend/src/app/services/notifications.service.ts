import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** US14 : format de NotificationDtos (pharmacy-service). */
export interface AppNotification {
  id: string; type: string; title: string; message: string; link: string | null; createdAt: string; read: boolean;
}
export interface MesNotifications { items: AppNotification[]; unread: number; }

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private headers() { return { Authorization: `Bearer ${sessionStorage.getItem('access_token') ?? ''}` }; }

  mes(): Observable<MesNotifications> {
    return this.http.get<MesNotifications>('/api/notifications/me', { headers: this.headers() });
  }
  lire(id: string): Observable<void> {
    return this.http.post<void>(`/api/notifications/${encodeURIComponent(id)}/read`, null, { headers: this.headers() });
  }
  toutLire(): Observable<void> {
    return this.http.post<void>('/api/notifications/read-all', null, { headers: this.headers() });
  }
}
