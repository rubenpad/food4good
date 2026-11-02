import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const uniqueName = () => `Recipe ${crypto.randomUUID()}`;
const recipeRow = (page: Page, name: string) =>
  page.getByRole("region", { name: "Recipes" }).locator("tbody tr", { hasText: name });
const nameInput = (page: Page) => page.getByRole("dialog").getByLabel("Name");
const preparationInput = (page: Page) => page.getByRole("dialog").getByLabel("Preparation time");
const save = (page: Page) => page.getByRole("dialog").getByRole("button", { name: "Save" }).click();

async function chooseIngredient(page: Page, row: number, ingredient: string) {
  await page.getByRole("dialog").getByRole("combobox", { name: "Ingredient" }).nth(row).click();
  await page.getByRole("option", { name: ingredient, exact: true }).click();
}

async function createRecipe(request: APIRequestContext, name: string) {
  const response = await request.post("/api/recipes", {
    data: {
      name,
      preparationTimeSeconds: 75,
      ingredients: [
        { ingredient: "onion", quantity: 1 },
        { ingredient: "rice", quantity: 2 },
      ],
    },
  });
  expect(response.status()).toBe(201);
}

test.beforeEach(async ({ page }) => {
  page.on("pageerror", (error) => {
    throw error;
  });
  await page.goto("/");
  await expect(recipeRow(page, "Cheeseburger")).toBeVisible();
});

test("lists the Recipes with no PrimeVue license notice", async ({ page }) => {
  await expect(recipeRow(page, "Grilled Chicken Salad")).toContainText("lettuce × 2");
  await expect(recipeRow(page, "Grilled Chicken Salad")).toContainText("1 min 30 s");
  await expect(page.getByText(/licen[sc]e/i)).toHaveCount(0);
});

test("creates a Recipe, showing API errors until the form is valid", async ({ page }) => {
  const name = uniqueName();
  await page.getByRole("button", { name: "New Recipe" }).click();

  await save(page);
  await expect(page.getByText("Choose an Ingredient for every row of the Recipe")).toBeVisible();

  await chooseIngredient(page, 0, "rice");
  await save(page);
  await expect(page.getByText("Every Recipe needs a name")).toBeVisible();

  await nameInput(page).fill(name);
  await preparationInput(page).fill("75");
  await page.getByRole("button", { name: "Add Ingredient" }).click();
  await chooseIngredient(page, 1, "onion");
  await save(page);

  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(recipeRow(page, name)).toContainText("1 min 15 s");
  await expect(recipeRow(page, name)).toContainText("onion × 1");
  await expect(recipeRow(page, name)).toContainText("rice × 1");
});

test("edits a Recipe from a pre-filled form, and the edit survives a reload", async ({ page, request }) => {
  const name = uniqueName();
  await createRecipe(request, name);
  await page.reload();

  await page.getByRole("button", { name: `Edit ${name}` }).click();
  await expect(nameInput(page)).toHaveValue(name);
  await preparationInput(page).fill("120");
  await page.getByRole("dialog").getByRole("button", { name: "Remove" }).first().click();
  await save(page);

  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(recipeRow(page, name)).toContainText("2 min");
  await expect(recipeRow(page, name)).not.toContainText("onion");

  await page.reload();
  await expect(recipeRow(page, name)).toContainText("rice × 2");
  await expect(recipeRow(page, name)).toContainText("2 min");
});

test("keeps the dialog open on a duplicate name, and Cancel changes nothing", async ({ page, request }) => {
  const name = uniqueName();
  await createRecipe(request, name);
  await page.reload();

  await page.getByRole("button", { name: `Edit ${name}` }).click();
  await nameInput(page).fill("Cheeseburger");
  await save(page);
  await expect(page.getByText("A Recipe named 'Cheeseburger' already exists")).toBeVisible();

  await page.getByRole("dialog").getByRole("button", { name: "Cancel" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(recipeRow(page, name)).toBeVisible();
});

test.describe("at phone width", () => {
  test.use({ viewport: { width: 390, height: 800 } });

  test("the page doesn't scroll sideways and the controls are reachable", async ({ page }) => {
    const { scrollWidth, innerWidth } = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
    }));
    expect(scrollWidth).toBeLessThanOrEqual(innerWidth);

    const newRecipe = await page.getByRole("button", { name: "New Recipe" }).boundingBox();
    expect(newRecipe!.x + newRecipe!.width).toBeLessThanOrEqual(390);

    await page.getByRole("button", { name: "Edit Cheeseburger" }).click();
    expect((await page.getByRole("dialog").boundingBox())!.width).toBeLessThanOrEqual(390);
  });
});
