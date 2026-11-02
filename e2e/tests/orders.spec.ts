import { expect, test, type Page } from "@playwright/test";

const panel = (page: Page, name: string) => page.getByRole("region", { name });
const history = (page: Page) => panel(page, "Order history");
const stockRow = (page: Page, ingredient: string) =>
  panel(page, "Food Storage").locator("tbody tr", { hasText: ingredient });

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await expect(stockRow(page, "cheese")).toBeVisible();
});

test("places the default single Order and shows it in the history", async ({ page }) => {
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  await expect(panel(page, "Place Orders").getByText("Accepted 1 of 1")).toBeVisible();
  await expect(history(page).locator("tbody tr").first()).toContainText(/Waiting for ingredients|Ready/);
});

test("places several Orders, and Food Storage shows their Reservations without a reload", async ({ page }) => {
  const reservedBefore = await totalReserved(page);

  await panel(page, "Place Orders").getByLabel("Quantity").fill("3");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  await expect(panel(page, "Place Orders").getByText("Accepted 3 of 3")).toBeVisible();
  await expect.poll(() => totalReserved(page)).toBeGreaterThan(reservedBefore);
});

test("shows the API's error for an invalid quantity", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("0");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  await expect(panel(page, "Place Orders").getByText("Quantity must be between 1 and 10000")).toBeVisible();
});

test("pages through the history and filters it by status", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("25");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();
  await expect(panel(page, "Place Orders").getByText("Accepted 25 of 25")).toBeVisible();

  await expect(history(page).locator("tbody tr")).toHaveCount(20);
  await history(page).getByRole("button", { name: "Older" }).click();
  await expect(history(page).getByRole("button", { name: "Newer" })).toBeEnabled();
  await history(page).getByRole("button", { name: "Newer" }).click();
  await expect(history(page).getByRole("button", { name: "Newer" })).toBeDisabled();

  await history(page).getByRole("combobox", { name: "Filter by status" }).click();
  await page.getByRole("option", { name: "Delivered" }).click();
  await expect(history(page).getByText("No Orders yet")).toBeVisible();
});

test("every panel fits a phone screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 });
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);
});

async function totalReserved(page: Page) {
  const cells = panel(page, "Food Storage").locator("tbody tr td:nth-child(3)");
  const values = await cells.allInnerTexts();
  return values.reduce((sum, value) => sum + Number(value), 0);
}
