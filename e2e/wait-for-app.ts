/** The gateway starts before the backend is ready, so wait until the API answers. */
export default async function waitForApp() {
  const url = `${process.env.BASE_URL ?? "http://localhost:8000"}/api/recipes`;
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    const response = await fetch(url).catch(() => null);
    if (response?.ok) return;
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error(`${url} did not become ready in time`);
}
