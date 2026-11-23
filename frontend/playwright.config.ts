import { defineConfig, devices } from '@playwright/test';

/**
 * TS07 : tests de bout en bout. Prérequis : `docker compose up -d` (tous les services) et `npx ng serve`.
 * Lancer : npm run e2e   |   regarder les tests : npm run e2e:ui   |   rapport : npx playwright show-report
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  timeout: 45_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1, // un seul navigateur : la VM fait déjà tourner tous les services
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    locale: 'fr-FR',
    timezoneId: 'Africa/Casablanca',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  // Lance `ng serve` s'il ne tourne pas déjà (réutilisé sinon), et attend qu'il réponde
  webServer: {
    command: 'npx ng serve --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 180_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
