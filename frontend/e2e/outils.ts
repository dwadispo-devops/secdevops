import { Page, expect } from '@playwright/test';

/** Données fictives uniquement (CDC §7.3). Mot de passe de 8 à 72 caractères (RegisterRequest). */
export const MOT_DE_PASSE = 'E2e-DwaDispo-2026';

export function emailUnique(prefixe = 'e2e.patient'): string {
  return `${prefixe}.${Date.now()}.${Math.floor(Math.random() * 10000)}@test.ma`;
}

/** Inscription par le formulaire : le site connecte le patient et l'envoie sur /scan. */
export async function inscrirePatient(page: Page, email: string): Promise<void> {
  await page.goto('/inscription');
  await page.locator('#firstName').fill('Test');
  await page.locator('#lastName').fill('E2E');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(MOT_DE_PASSE);
  await page.locator('#confirm').fill(MOT_DE_PASSE);
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page).toHaveURL(/\/scan/);
}

export async function connecter(page: Page, email: string, motDePasse = MOT_DE_PASSE): Promise<void> {
  await page.goto('/connexion');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(motDePasse);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

export function jeton(page: Page): Promise<string | null> {
  return page.evaluate(() => sessionStorage.getItem('access_token'));
}
