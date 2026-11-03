import { expect, test, type Page } from "@playwright/test";

const panel = (page: Page, name: string) => page.getByRole("region", { name });
const history = (page: Page) => panel(page, "Order history");
const stockRow = (page: Page, ingredient: string) =>
  panel(page, "Food Storage").getByRole("table").first().locator("tbody tr", { hasText: ingredient });

test.setTimeout(240_000);

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await expect(stockRow(page, "cheese")).toBeVisible();
});

test("places the default single Order and shows it in the history", async ({ page }) => {
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  await expect(panel(page, "Place Orders").getByText(/Accepted 1 of 1\./)).toBeVisible();
  await expect(history(page).locator("tbody tr").first()).toContainText(
    /Waiting for ingredients|Ready|Preparing|Delivered/,
  );
});

test("buys what Food Storage lacks, and shows the Purchases without a reload", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("10");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  const purchases = panel(page, "Purchase history").locator("tbody tr");
  await expect(purchases.first()).toContainText(/tomato|lemon|potato|rice|ketchup|lettuce|onion|cheese|meat|chicken/, {
    timeout: 30_000,
  });
  await expect(purchases.first().locator("td").nth(1)).toHaveText(/^[1-9][0-9]*$/);
});

test("Orders that needed buying still get delivered", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("10");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();
  await expect(panel(page, "Place Orders").getByText(/Accepted 10 of 10\./)).toBeVisible();

  await expect(panel(page, "Kitchen").getByRole("heading", { name: "Waiting for ingredients (0)" })).toBeVisible({
    timeout: 120_000,
  });
  await expect(panel(page, "Kitchen").getByRole("heading", { name: "Ready (0)" })).toBeVisible({ timeout: 180_000 });
  await expect(panel(page, "Kitchen").getByRole("heading", { name: "Preparing (0)" })).toBeVisible({ timeout: 60_000 });
});

test("shows the API's error for an invalid quantity", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("0");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  await expect(panel(page, "Place Orders").getByText("Quantity must be between 1 and 10000")).toBeVisible();
});

test("pages through the history and filters it by status", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("25");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();
  await expect(panel(page, "Place Orders").getByText(/Accepted 25 of 25\./)).toBeVisible();

  await expect(history(page).locator("tbody tr")).toHaveCount(20);
  await history(page).getByRole("button", { name: "Older" }).click();
  await expect(history(page).getByRole("button", { name: "Newer" })).toBeEnabled();
  await history(page).getByRole("button", { name: "Newer" }).click();
  await expect(history(page).getByRole("button", { name: "Newer" })).toBeDisabled();

  await history(page).getByRole("combobox", { name: "Filter by status" }).click();
  await page.getByRole("option", { name: "Delivered" }).click();
  await expect(history(page).locator("tbody tr").first()).toContainText("Delivered", { timeout: 120_000 });
});

test("the Kitchen shows its Cooks working and Orders in each status", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("12");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  const kitchen = panel(page, "Kitchen");
  await expect(kitchen.getByText(/Cooks busy: [1-4] of 4/)).toBeVisible({ timeout: 60_000 });
  await expect(kitchen.getByRole("region", { name: "Preparing" }).getByRole("listitem").first()).toBeVisible({
    timeout: 60_000,
  });
  await expect(kitchen.getByRole("region", { name: "Ready" })).toBeVisible();
});

test("shows a read-only Backlog and Maximum wait, and a live estimate of what fits", async ({ page }) => {
  await expect(panel(page, "Kitchen").getByText(/Backlog: .+ \(estimated\) · Maximum wait: 5 min/)).toBeVisible();
  await expect(panel(page, "Place Orders").getByText(/Estimate: about \d+ more Orders fit right now/)).toBeVisible();
});

test("says how many were accepted when only some fit", async ({ page }) => {
  await panel(page, "Place Orders").getByLabel("Quantity").fill("9000");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  await expect(panel(page, "Place Orders").getByText(/Accepted \d+ of 9000\. The rest would go past/)).toBeVisible();
});

// Dishes finishing keep freeing room in a running Kitchen, so the answer is fixed here. The API tests
// prove when a Kitchen really is full; this only checks what the Manager is told.
test("explains when the Kitchen is full", async ({ page }) => {
  await page.route("**/api/orders", async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    await route.fulfill({ json: { requested: 5, accepted: 0, orderIds: [], estimatedWaitSeconds: 300 } });
  });

  await panel(page, "Place Orders").getByLabel("Quantity").fill("5");
  await panel(page, "Place Orders").getByRole("button", { name: "Place Orders" }).click();

  await expect(
    panel(page, "Place Orders").getByText("The Kitchen is full: accepted 0 of 5. Estimated wait to clear the Backlog: 5 min."),
  ).toBeVisible();
});

test("every panel fits a phone screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 });
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);
});
