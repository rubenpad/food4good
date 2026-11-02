export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function parseResponse<T>(response: Response, description: string): Promise<T> {
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      problem?.detail ?? `${description} failed with ${response.status}`,
    );
  }
  return (await response.json()) as T;
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`/api${path}`, { headers: { Accept: "application/json" } });
  return parseResponse<T>(response, `GET /api${path}`);
}

export async function sendJson<T>(method: "POST" | "PUT", path: string, body: unknown): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return parseResponse<T>(response, `${method} /api${path}`);
}
