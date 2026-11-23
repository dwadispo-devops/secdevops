import { test, expect } from '@playwright/test';

const API = process.env['E2E_API_URL'] ?? 'http://localhost:8080';

test('TS07 : chaque service publie sa documentation via la gateway', async ({ request }) => {
  for (const service of ['auth', 'pharmacy', 'prescription', 'medication']) {
    const reponse = await request.get(`${API}/docs/${service}/v3/api-docs`);
    expect(reponse.status(), `documentation de ${service}`).toBe(200);
    expect((await reponse.json()).info.title).toContain('DwaDispo');
  }
});

test('TS07 : la page Swagger unique propose les 4 services', async ({ page }) => {
  await page.goto(`${API}/swagger-ui.html`);
  await expect(page.getByLabel('Select a definition').locator('option')).toHaveCount(4);
  await expect(page.getByText(/DwaDispo - Pharmacies/).first()).toBeVisible();
});
