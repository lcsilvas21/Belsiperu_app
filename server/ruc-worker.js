/**
 * BELSIPERU — pasarela privada para consulta de RUC en endpoint.pe.
 * Cloudflare Worker: configurar ENDPOINT_PE_API_KEY y APP_ACCESS_CODE
 * como secretos en el panel de Cloudflare, nunca en el repositorio.
 * La app hace GET /ruc/NNNNNNNNNNN o GET /dni/NNNNNNNN.
 * Ambas peticiones usan X-Belsiperu-Access y la misma clave secreta de endpoint.pe.
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
    const route = /^\/(ruc|dni)\/([0-9]+)$/.exec(url.pathname);
    if (!route ||
        (route[1] === "ruc" && !/^[0-9]{11}$/.test(route[2])) ||
        (route[1] === "dni" && !/^[0-9]{8}$/.test(route[2]))) {
      return respuesta({ success: false, message: "Ruta incorrecta: el RUC debe tener 11 dígitos y el DNI 8." }, 422);
    }
    const tipo = route[1];
    const documento = route[2];
    try {
      const upstream = await fetch("https://api.endpoint.pe/v1/" + tipo + "/" + documento, {
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
      if (tipo === "dni") {
        const nombreCompleto = typeof data.nombre_completo === "string"
          ? data.nombre_completo.trim() : "";
        const nombres = typeof data.nombres === "string" ? data.nombres.trim() : "";
        const paterno = typeof data.apellido_paterno === "string" ? data.apellido_paterno.trim() : "";
        const materno = typeof data.apellido_materno === "string" ? data.apellido_materno.trim() : "";
        const nombre = nombreCompleto || [nombres, paterno, materno].filter(Boolean).join(" ");
        if (!nombre) return respuesta({ success: false, message: "La consulta no devolvió nombres." }, 502);
        return respuesta({
          success: true,
          message: "Consulta exitosa",
          data: {
            dni: documento,
            nombres,
            apellido_paterno: paterno,
            apellido_materno: materno,
            nombre_completo: nombre,
            digito_verificador: data.digito_verificador || "",
            ruc: typeof data.ruc === "string" ? data.ruc : null,
          },
        }, 200);
      }
      return respuesta({
        success: true,
        message: "Consulta exitosa",
        data: {
          ruc: String(data.ruc || documento),
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
