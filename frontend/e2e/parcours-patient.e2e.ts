import { test, expect } from '@playwright/test';
import { connecter, emailUnique, inscrirePatient, jeton } from './outils';

test.describe('Parcours patient', () => {
  test("l'accueil s'affiche avec le menu des visiteurs", async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/DwaDispo/);
    await expect(page.getByRole('link', { name: 'Connexion', exact: true }).first()).toBeVisible();
    await expect(page.getByRole('link', { name: 'Créer un compte' }).first()).toBeVisible();
  });

  test('une page patient exige une connexion', async ({ page }) => {
    await page.goto('/alertes');
    await expect(page).toHaveURL(/\/connexion\?retour=/);
  });

  test("inscription, puis nouvelle connexion d'un patient", async ({ page }) => {
    const email = emailUnique();
    await inscrirePatient(page, email);
    expect(await jeton(page)).toBeTruthy();
    await expect(page.getByRole('link', { name: 'Mon profil' })).toBeVisible();

    // On ferme la session, puis on se reconnecte avec le même compte
    await page.evaluate(() => sessionStorage.clear());
    await connecter(page, email);
    await expect.poll(() => jeton(page)).toBeTruthy();
    await expect(page.getByRole('link', { name: 'Mon profil' })).toBeVisible();
  });

  test('un mauvais mot de passe est refusé', async ({ page }) => {
    await connecter(page, emailUnique('e2e.inconnu'), 'MauvaisMotDePasse1');
    await expect(page.getByRole('alert').filter({ hasText: 'Identifiants incorrects.' })).toBeVisible();
    expect(await jeton(page)).toBeNull();
  });
});
