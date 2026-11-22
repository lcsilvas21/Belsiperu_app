/**
 * BELSIPERU — pasarela privada para consulta de RUC en endpoint.pe.
 * Cloudflare Worker: configurar ENDPOINT_PE_API_KEY y APP_ACCESS_CODE
 * como secretos en el panel de Cloudflare, nunca en el repositorio.
 * La app hace GET /ruc/NNNNNNNNNNN con X-Belsiperu-Access.
 */
function respuesta(json, status) {
  return new Response(JSON.stringify(json), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
function igualConstante(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  // Compara sin salir anticipadamente con cada carácter; el código no es una API key.
  let diff = a.length ^ b.length;
  const max = Math.max(a.length, b.length);
  for (let i = 0; i < max; i++) {
    diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return diff === 0;
}
export default {
  async fetch(request, env) {
    if (request.method !== "GET") {
      return respuesta({ success: false, message: "Método no permitido." }, 405);
    }
    if (!env.ENDPOINT_PE_API_KEY || !env.APP_ACCESS_CODE || env.APP_ACCESS_CODE.length < 16) {
      return respuesta({ success: false, message: "El servicio RUC aún no está configurado." }, 503);
    }
    // Importante: comprobar autorización ANTES de consultar al proveedor.
    const access = request.headers.get("X-Belsiperu-Access") || "";
    if (!igualConstante(access, env.APP_ACCESS_CODE)) {
      return respuesta({ success: false, message: "No autorizado." }, 401);
    }
    const url = new URL(request.url);
    const match = /^\/ruc\/([0-9]{11})$/.exec(url.pathname);
    if (!match) {
      return respuesta({ success: false, message: "Ruta incorrecta o RUC inválido." }, 422);
    }
    try {
      const upstream = await fetch("https://api.endpoint.pe/v1/ruc/" + match[1], {
        method: "GET",
        headers: {
          Authorization: "Bearer " + env.ENDPOINT_PE_API_KEY,
          Accept: "application/json",
        },
        signal: AbortSignal.timeout(10000),
      });
      const body = await upstream.json().catch(() => null);
      if (!body || typeof body !== "object") {
        return respuesta({ success: false, message: "Respuesta del proveedor no válida." }, 502);
      }
      if (!upstream.ok || body.success !== true) {
        return respuesta({
          success: false,
          message: typeof body.message === "string" ? body.message : "No se pudo consultar.",
        }, upstream.status === 200 ? 502 : upstream.status);
      }
      const data = body.data || {};
      return respuesta({
        success: true,
        message: "Consulta exitosa",
        data: {
          ruc: String(data.ruc || match[1]),
          razon_social: data.razon_social || "",
          direccion: data.direccion || "",
          direccion_completa: data.direccion_completa || data.direccion || "",
          estado: data.estado || "",
          condicion: data.condicion || "",
        },
      }, 200);
    } catch (_error) {
      return respuesta({ success: false, message: "Servicio temporalmente no disponible." }, 502);
    }
  },
};
