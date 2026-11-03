import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const panel = (page: Page, name: string) => page.getByRole("region", { name });
const historyRows = (page: Page) => panel(page, "Order history").locator("tbody tr[data-p-index]");

async function placeOutsideTheDashboard(request: APIRequestContext, quantity: number) {
  const response = await request.post("/api/orders", { data: { quantity } });
  expect(response.status()).toBe(200);
}

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await expect(panel(page, "Food Storage").getByRole("table").first().locator("tbody tr").first()).toBeVisible();
});

test("shows changes made elsewhere without polling for them", async ({ page, request }) => {
  const polled: string[] = [];
  page.on("request", (sent) => {
    if (/\/api\/(storage|orders)/.test(sent.url())) polled.push(sent.url());
  });
  await page.waitForTimeout(5_000);
  expect(polled).toEqual([]);

  const before = await historyRows(page).count();
  const stockBefore = await panel(page, "Food Storage").getByRole("table").first().innerText();
  await placeOutsideTheDashboard(request, 3);

  await expect(historyRows(page)).toHaveCount(before + 3, { timeout: 5_000 });
  await expect(panel(page, "Food Storage").getByRole("table").first()).not.toHaveText(stockBefore, { timeout: 5_000 });
  expect(polled.filter((url) => url.includes("/api/storage"))).toEqual([]);
});

test("a status change reaches a row already on screen", async ({ page, request }) => {
  await placeOutsideTheDashboard(request, 1);
  await expect(historyRows(page).first().getByText("Delivered")).toBeVisible({ timeout: 120_000 });
});

test("catches up after losing its connection", async ({ page, request, context }) => {
  const before = await historyRows(page).count();

  await context.setOffline(true);
  await page.waitForTimeout(1_000);
  await placeOutsideTheDashboard(request, 2);
  await page.waitForTimeout(1_000);
  // What's on screen stays, even if a refresh fails while offline.
  await expect(historyRows(page)).toHaveCount(before);

  await context.setOffline(false);
  await expect(historyRows(page)).toHaveCount(before + 2, { timeout: 15_000 });
});
