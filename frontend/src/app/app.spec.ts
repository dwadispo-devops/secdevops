import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the route outlet', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });

  it('affiche le menu avec un lien de connexion si personne n’est connecté (TS04)', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav')!;
    expect(nav.textContent).toContain('Médicaments');
    expect(nav.textContent).toContain('Connexion');
    expect(nav.querySelector('button')).toBeNull();
  });

  it('propose la déconnexion quand un jeton est présent', async () => {
    sessionStorage.setItem('access_token', 'jwt');
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const boutons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav button'));
    expect(boutons.some(b => b.textContent?.includes('Déconnexion'))).toBe(true);
    expect(boutons.some(b => b.textContent?.includes('🔔'))).toBe(true); // US14 : cloche visible une fois connecté
  });
});
