import { test, expect } from '@playwright/test';
import { emailUnique, inscrirePatient } from './outils';

// Position simulée (centre de Marrakech) et géolocalisation autorisée
test.use({ geolocation: { latitude: 31.6295, longitude: -7.9811 }, permissions: ['geolocation'] });

test('US22 : un patient crée puis supprime une alerte de retour en stock', async ({ page }) => {
  await inscrirePatient(page, emailUnique());
  await page.goto('/alertes');
  await expect(page.getByRole('heading', { name: 'Mes alertes', exact: true })).toBeVisible();

  // Chercher le médicament dans le catalogue CNOPS et le choisir
  await page.getByPlaceholder('ex. Doliprane').fill('doliprane');
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await page.getByRole('button', { name: 'Choisir' }).first().click();

  // Créer l'alerte : la position simulée est envoyée au serveur
  await page.getByRole('button', { name: /M.alerter quand il revient/ }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Alerte créée' })).toBeVisible();
  await expect(page.getByText(/En attente/)).toBeVisible();

  // La supprimer
  const supprimer = page.getByRole('button', { name: 'Supprimer' });
  await expect(supprimer).toHaveCount(1);
  await supprimer.click();
  await expect(supprimer).toHaveCount(0);
});
