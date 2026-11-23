# BELSIPERU Gestión Comercial — registro de Google Play Console desde Perú

**Versión del documento:** 23 de noviembre de 2026  
**Estado:** guía para el titular; Google Play Console todavía no está registrada ni conectada.  
**Proyecto:** rama `feat/google-play-public-v1-25`, código Android `pe.belsiperu.gestioncomercial` propuesto, no confirmado para publicación.

## 1. Elegir la titularidad ANTES de pagar

### Organización (si BELSIPERU S.A.C. será la propietaria legal)
- Elegir **Organización** en el registro.
- Necesitará razón social y dirección legal verificables, documentos de organización, identificación oficial del representante autorizado, teléfono, correo y **sitio web de la empresa**.
- Es obligatorio un **D-U-N-S** válido. Buscar primero si la persona jurídica ya dispone de él. Solicitarlo directamente a Dun & Bradstreet es gratis y puede tardar hasta 30 días.
- Los datos del perfil de pagos deben coincidir exactamente con Dun & Bradstreet y documentos.
- No registrarla a nombre de otra persona si el negocio conservará los derechos y titularidad.

### Personal (si se publica como producto particular)
- Elegir **Personal**, proporcionando identidad y contactos personales verificables.
- No solicita D-U-N-S.
- Google Play requiere verificar el teléfono Android con la app Play Console.
- Una cuenta personal nueva debe completar prueba cerrada con **12 testers opt-in continuos durante al menos 14 días**, y después solicitar acceso a producción.
- Los datos legales del desarrollador pueden mostrarse públicamente según políticas regionales y monetización.

La aplicación puede descargarse públicamente en ambos casos; la elección depende de quién será su titular y responsable.

## 2. Registrar la cuenta
1. Abrir **https://play.google.com/console/signup** con una cuenta Google del titular, protegida con verificación de dos pasos.
2. Aceptar el Acuerdo de Distribución para Desarrolladores.
3. Escoger Personal u Organización según la titularidad legal (no por rapidez de registro).
4. Crear/vincular un perfil de Google Payments con el nombre/dirección **reales** de la persona o entidad.
5. Completar campos, verificaciones de documentos, teléfono y correo; seguir instrucciones exactas de Play Console.
6. Abonar **US$ 25 una sola vez** mediante tarjeta admitida. No usar tarjeta prepaga ni sitios de terceros.
7. Conservar el recibo y esperar la confirmación de verificación.

**Perú está admitido como país de registro.** No enviar contraseñas, tarjetas, fotos de documentos, almacenes de firma ni códigos de verificación por chat ni colocarlos en GitHub.

## 3. Crear la ficha de aplicación después de verificar la cuenta
- Botón *Crear aplicación / Create app*.
- Nombre visible: **BELSIPERU Gestión Comercial**.
- Idioma predeterminado: Español.
- Tipo: Aplicación (no juego).
- Precio: Gratuita para la primera publicación (la monetización futura se diseñará y declarará por separado).
- Identificador **propuesto**: `pe.belsiperu.gestioncomercial`. Confirmar que se usará definitivamente ANTES de registrar el package. Una vez publicado, no puede cambiarse a voluntad.
- E-mail de soporte de clientes **real** y verificado: pendiente de proporcionar por el titular.
- Completar política de privacidad pública HTTPS y sección *Seguridad de los datos* basada en una auditoría real de SDK/consultas.
- Preparar icono de tienda, capturas auténticas de la V1.25 y gráfico promocional.
- Declarar que genera proformas comerciales, **no comprobantes SUNAT electrónicos**.

## 4. Checklist en el proyecto técnico
- [x] App pública de prueba `1.25.0-rc1` compilada (API 36).
- [x] AAB de validación de release **sin firma** (NO se carga en Google Play).
- [x] Workflow manual para AAB de producción firmado, con secretos privados.
- [x] Formulario para que cada empresa configure sus propios datos y logotipo.
- [x] Documentos preliminares: `docs/FICHA_GOOGLE_PLAY_ES.md`, `docs/PRIVACIDAD_BORRADOR.md`, `docs/FIRMA_GOOGLE_PLAY.md`.
- [ ] Confirmar nombre legal del titular de publicación, tipo de cuenta y capacidad de verificación.
- [ ] Habilitar un sitio web y URL HTTPS pública para política de privacidad.
- [ ] Confirmar correo público de soporte y contacto de privacidad.
- [ ] Auditoría final de bibliotecas/SDK y formulario de Data safety.
- [ ] Generar la upload key en un equipo seguro y guardarla fuera del repositorio.
- [ ] Configurar secrets en GitHub Actions y generar un **AAB firmado**, nunca publicar uno sin firma.
- [ ] Comprobar pruebas y restauración de datos en distintos teléfonos.
- [ ] Test interno o cerrado en Play Console según requisitos de la cuenta.
- [ ] Publicar solo después de revisión y autorización del titular.

## 5. Links oficiales (verificados el 23/11/2026)
- Registro: https://play.google.com/console/signup
- Inscripción y pago: https://support.google.com/googleplay/android-developer/answer/6112435
- Tipo de cuenta: https://support.google.com/googleplay/android-developer/answer/13634885
- Documentación para registrarse: https://support.google.com/googleplay/android-developer/answer/13628312
- Documentación requerida en Perú: https://support.google.com/googleplay/android-developer/answer/15633622?co=GENIE.CountryCode%3DPE
- Prueba cerrada de cuentas personales: https://support.google.com/googleplay/android-developer/answer/14151465
- Crear ficha de aplicación: https://support.google.com/googleplay/android-developer/answer/9859152
- Política de datos: https://support.google.com/googleplay/android-developer/answer/10144311

**Estado final:** preparación documental disponible. La creación/pago/verificación de la cuenta es una acción exclusiva del titular. No hay publicación automática.
