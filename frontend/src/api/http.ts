export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`/api${path}`, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`GET /api${path} failed with ${response.status}`);
  }
  return (await response.json()) as T;
}
