# BELSIPERU — activar consulta de RUC mediante endpoint.pe

La app Android incluye **Clientes → Consultar RUC**, autocompletado de **razón social** y **dirección**, y visualización de **estado** y **condición**. También se puede seguir ingresando clientes manualmente.

## Para activar la consulta real

1. Crea una cuenta en https://endpoint.pe y habilita la API de RUC. Genera una **API key** en tu panel.
2. Crea una cuenta en https://dash.cloudflare.com y despliega un **Worker** usando el archivo `ruc-worker.js` de esta carpeta (Workers & Pages → Create).
3. En Settings → Variables and Secrets de ese Worker configura **dos secretos**:
   - `ENDPOINT_PE_API_KEY`: la API key de endpoint.pe (debe quedar solo en Cloudflare).
   - `APP_ACCESS_CODE`: un código aleatorio distinto de al menos 16 caracteres (recomendados 32 o más), para proteger las consultas y los créditos del proveedor.
4. Abre **BELSIPERU RUC → Inicio → Configuración → Configurar consulta de RUC**. Pega solamente la URL HTTPS pública del Worker y el código `APP_ACCESS_CODE` (no el token del proveedor).
5. En **Clientes**, ingresa un RUC real, pulsa **Consultar RUC**, revisa los campos y luego **Guardar cliente**.

**No compartas las claves en el chat, en capturas ni las subas a GitHub.** No coloques la key del proveedor en el código Android ni en el Worker. Debe ser un secreto de Cloudflare.

El código de acceso **no** es la key de endpoint.pe. Se almacena en las preferencias privadas de la app, suficientes para un prototipo de uso personal, pero no equivale a almacenamiento seguro para despliegue comercial. Antes de distribución comercial se recomienda autenticación de usuario, protección reforzada del código de acceso mediante Android Keystore y límites de uso / rate limiting en Cloudflare.

La API solo se consume al tocar «Consultar RUC». No se guarda una ficha nueva hasta que el usuario pulsa «Guardar cliente». Un error de red no impide el registro manual. El RUC o DNI ya registrado se actualiza en lugar de duplicarse.

El Worker **no incluye ningún token real** y se proporciona como código fuente para desplegar. Sin despliegue ni claves la función queda sin activar. La app no se conecta directamente con endpoint.pe porque su propia documentación prohíbe exponer tokens en aplicaciones móviles.

Documentación oficial:
- https://endpoint.pe/docs/apis/ruc
- https://endpoint.pe/docs/autenticacion
- https://endpoint.pe/docs/buenas-practicas

El servicio puede cobrar por consultas o limitar el uso según el plan contratado.


## Consulta DNI (versión 1.6)

El mismo Cloudflare Worker y los mismos secretos que ya usan las consultas RUC sirven para DNI. Se debe **reemplazar el código del Worker publicado** por el archivo `server/ruc-worker.js` actualizado en la rama `feat/consulta-dni-automatico`; crear la APK no despliega cambios a Cloudflare.

- RUC: `GET /ruc/{11_digitos}` → razón social y dirección fiscal.
- DNI: `GET /dni/{8_digitos}` → nombre completo, nombres y apellidos, dígito verificador y RUC opcional.
- Ambas rutas requieren la cabecera `X-Belsiperu-Access` y reutilizan `ENDPOINT_PE_API_KEY` y `APP_ACCESS_CODE`.
- En la aplicación, abrir **Clientes**, ingresar DNI de 8 dígitos y tocar **Consultar DNI**. El nombre se completa, pero la dirección no se obtiene con esa API. No se registra nada hasta tocar **Guardar cliente**.
- No pegar claves en archivos fuente ni enviar datos personales al chat.
- El servicio de DNI de endpoint.pe requiere una finalidad legítima y observar la Ley N.° 29733.

Referencia: https://endpoint.pe/docs/apis/dni
