import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { NotificationBellComponent } from './notification-bell';
import { AppNotification, NotificationsService } from '../services/notifications.service';

const notif = (id: string, read: boolean, link: string | null = '/suivi-demande/x'): AppNotification =>
  ({ id, type: 'REPONSE_PHARMACIE', title: 'Médicaments disponibles', message: 'Atlas a vos médicaments (10.00 DH).', link, createdAt: '', read });

describe('Notifications (US14)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('access_token', 'jwt');
    TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('interroge /api/notifications/me avec le jeton', () => {
    TestBed.inject(NotificationsService).mes().subscribe();
    const req = http.expectOne('/api/notifications/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt');
    req.flush({ items: [], unread: 0 });
  });

  it('ouvrir une notification non lue la marque comme lue et décrémente le compteur', () => {
    const bell = TestBed.createComponent(NotificationBellComponent).componentInstance;
    bell.items.set([notif('n1', false), notif('n2', false)]);
    bell.nonLues.set(2);
    bell.ouvrir(bell.items()[0]);
    http.expectOne('/api/notifications/n1/read').flush(null);
    expect(bell.nonLues()).toBe(1);
    expect(bell.items()[0].read).toBe(true);
  });

  it('tout marquer comme lu remet le compteur à zéro', () => {
    const bell = TestBed.createComponent(NotificationBellComponent).componentInstance;
    bell.items.set([notif('n1', false)]);
    bell.nonLues.set(1);
    bell.toutLire();
    http.expectOne('/api/notifications/read-all').flush(null);
    expect(bell.nonLues()).toBe(0);
  });
});
