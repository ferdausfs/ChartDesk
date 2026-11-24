interface Env {
  DB: D1Database;
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

function badRequest(message: string): Response {
  return json({ ok: false, error: message }, 400);
}

function uid(): string {
  return crypto.randomUUID();
}

async function fetchPrices(symbols: string[]): Promise<Map<string, number>> {
  const out = new Map<string, number>();
  if (!symbols.length) return out;
  const url = `https://api.binance.com/api/v3/ticker/price?symbols=${encodeURIComponent(JSON.stringify(symbols))}`;
  const res = await fetch(url, { cf: { cacheTtl: 10 } });
  if (!res.ok) return out;
  const rows = (await res.json()) as Array<{ symbol: string; price: string }>;
  for (const row of rows) out.set(row.symbol, Number(row.price));
  return out;
}

async function evaluateAlerts(env: Env): Promise<{ checked: number; triggered: number }> {
  const result = await env.DB.prepare(
    "SELECT id, device_token, symbol, direction, price FROM alerts WHERE active = 1",
  ).all<{ id: string; device_token: string; symbol: string; direction: string; price: number }>();
  const alerts = result.results ?? [];
  if (!alerts.length) return { checked: 0, triggered: 0 };
  const symbols = Array.from(new Set(alerts.map((a) => a.symbol)));
  const prices = await fetchPrices(symbols);
  const now = Date.now();
  let triggered = 0;
  const statements: D1PreparedStatement[] = [];
  for (const alert of alerts) {
    const price = prices.get(alert.symbol);
    if (price === undefined) continue;
    const hit = alert.direction === "above" ? price >= alert.price : price <= alert.price;
    if (!hit) continue;
    statements.push(
      env.DB.prepare("UPDATE alerts SET active = 0, triggered_at = ? WHERE id = ?").bind(now, alert.id),
    );
    statements.push(
      env.DB.prepare(
        "INSERT INTO alert_events (id, alert_id, device_token, symbol, direction, target, price, triggered_at, seen) VALUES (?,?,?,?,?,?,?,?,0)",
      ).bind(uid(), alert.id, alert.device_token, alert.symbol, alert.direction, alert.price, price, now),
    );
    triggered += 1;
  }
  if (statements.length) await env.DB.batch(statements);
  return { checked: alerts.length, triggered };
}

async function proxyBinance(path: string, params: URLSearchParams): Promise<Response> {
  const url = `https://api.binance.com${path}?${params.toString()}`;
  const res = await fetch(url, { cf: { cacheTtl: 5, cacheEverything: true } });
  const body = await res.text();
  return new Response(body, {
    status: res.status,
    headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=5", ...CORS_HEADERS },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS_HEADERS });
    const url = new URL(request.url);
    const path = url.pathname;

    try {
      if (path === "/api/health") {
        return json({ ok: true, service: "chartdesk-api", time: new Date().toISOString() });
      }

      if (path === "/api/candles") {
        const symbol = (url.searchParams.get("symbol") ?? "BTCUSDT").toUpperCase();
        const interval = url.searchParams.get("interval") ?? "1m";
        const limit = Math.min(1000, Math.max(20, Number(url.searchParams.get("limit") ?? "500")));
        const params = new URLSearchParams({ symbol, interval, limit: String(limit) });
        return proxyBinance("/api/v3/klines", params);
      }

      if (path === "/api/symbols") {
        return proxyBinance("/api/v3/exchangeInfo", new URLSearchParams({ permissions: "SPOT" }));
      }

      if (path === "/api/alerts/check" && request.method === "POST") {
        const result = await evaluateAlerts(env);
        return json({ ok: true, ...result });
      }

      if (path === "/api/alerts" && request.method === "POST") {
        const body = (await request.json()) as {
          deviceToken?: string;
          symbol?: string;
          direction?: string;
          price?: number;
        };
        if (!body.deviceToken || !body.symbol || !body.direction || !body.price) {
          return badRequest("deviceToken, symbol, direction and price are required");
        }
        if (body.direction !== "above" && body.direction !== "below") return badRequest("direction must be above|below");
        const id = uid();
        await env.DB.prepare(
          "INSERT INTO alerts (id, device_token, symbol, direction, price, active, created_at) VALUES (?,?,?,?,?,1,?)",
        ).bind(id, body.deviceToken, body.symbol.toUpperCase(), body.direction, body.price, Date.now()).run();
        return json({ ok: true, id });
      }

      if (path === "/api/alerts" && request.method === "GET") {
        const token = url.searchParams.get("token");
        if (!token) return badRequest("token is required");
        const rows = await env.DB.prepare(
          "SELECT id, symbol, direction, price, active, created_at, triggered_at FROM alerts WHERE device_token = ? ORDER BY created_at DESC",
        ).bind(token).all();
        return json({ ok: true, alerts: rows.results ?? [] });
      }

      if (path.startsWith("/api/alerts/") && request.method === "DELETE") {
        const id = path.slice("/api/alerts/".length);
        const token = url.searchParams.get("token");
        if (!token) return badRequest("token is required");
        await env.DB.prepare("DELETE FROM alerts WHERE id = ? AND device_token = ?").bind(id, token).run();
        return json({ ok: true });
      }

      if (path === "/api/alerts/events" && request.method === "GET") {
        const token = url.searchParams.get("token");
        const since = Number(url.searchParams.get("since") ?? "0");
        if (!token) return badRequest("token is required");
        const rows = await env.DB.prepare(
          "SELECT id, symbol, direction, target, price, triggered_at FROM alert_events WHERE device_token = ? AND seen = 0 AND triggered_at > ? ORDER BY triggered_at",
        ).bind(token, since).all();
        const events = rows.results ?? [];
        if (events.length) {
          const ids = events.map((e) => (e as { id: string }).id);
          await env.DB.prepare(
            `UPDATE alert_events SET seen = 1 WHERE id IN (${ids.map(() => "?").join(",")})`,
          ).bind(...ids).run();
        }
        return json({ ok: true, events });
      }

      return json({ ok: false, error: "not found" }, 404);
    } catch (error) {
      return json({ ok: false, error: String(error) }, 500);
    }
  },

  async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(evaluateAlerts(env).then(() => undefined));
  },
};
