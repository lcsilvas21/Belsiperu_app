# BELSIPERU Gestión Comercial — preparación para Google Play

**Estado: candidato técnico; no publicado ni aprobado por Google.**

**Registro desde Perú:** consulta [guía paso a paso para crear Play Console](REGISTRO_PLAY_CONSOLE_PERU.md).

## Identidad propuesta
- Nombre: BELSIPERU Gestión Comercial
- ID tentativo y permanente al publicar: `pe.belsiperu.gestioncomercial`
- Plataforma: Android, Kotlin, aplicación local por instalación
- Versión preliminar: 1.25.0-rc1 (`versionCode=33`)
- API objetivo: Android 16, API 36
- Público inicial: negocios de Perú; cálculos de IGV y consultas RUC/DNI son específicos de Perú
- Sin sincronización en la nube, usuarios empresariales remotos ni pagos integrados

**Importante:** el package ID es irreversible después de crear la ficha de Google Play. Confirmar su elección antes de la primera publicación. Las instalaciones antiguas de prueba tienen IDs diferentes y no transfieren automáticamente datos.

## Compilación
- CI: `.github/workflows/android-build.yml` comprueba APK debug y AAB release sin firma (**no publicar el AAB sin firma**).
- Publicación: `.github/workflows/google-play-release.yml` se ejecuta manualmente, nunca publica directamente.
- **Nunca almacenar** `.jks`, certificados privados, contraseñas ni base64 de claves en el repositorio.
- Crear una **upload key** de la que el titular conserve copia segura; habilitar Play App Signing en Play Console.
- Configurar secretos GitHub Actions:
  - `BELSI_UPLOAD_KEYSTORE_BASE64` — contenido base64 del archivo de clave de subida
  - `BELSI_UPLOAD_KEY_ALIAS` — alias de la clave
  - `BELSI_UPLOAD_STORE_PASSWORD` — contraseña del almacén
  - `BELSI_UPLOAD_KEY_PASSWORD` — contraseña de la clave
- Lanzar manualmente workflow *Google Play - AAB firmado (manual)* desde GitHub Actions y descargar artefacto AAB.
- **La firma y la inscripción no están configuradas por defecto**. El responsable de Play Console debe completarlas.
- No automatizar publicación en producción sin revisión explícita del titular.

## Checklist de seguridad/privacidad
- [ ] Confirmar titular legal, correo de soporte y titular de derechos de la marca.
- [ ] Publicar la política de privacidad en URL HTTPS pública, estable y no editable.
- [ ] Revisar todos los SDK y servicios externos para Declaración de seguridad de datos de Play.
- [ ] Revisar funcionamiento real de escaneo y permisos de Play Services.
- [ ] Revisar recuperación PIN y restauración de respaldos en teléfonos físicos.
- [ ] Probar eliminación local con datos ficticios (¡irreversible!).
- [ ] Probar onboarding de empresa sin RUC y con RUC válido.
- [ ] Comprobar que ningún PDF comparte marca, RUC o logo de otra empresa.
- [ ] Probar Excel con cientos de productos y archivos mal formados.
- [ ] Probar vista/impresión A4 horizontal, A5 y Carta con 1 y varios productos.
- [ ] Probar en Android 10, 13, 15 y 16; revisar edge-to-edge y 16 KB page size si aplica.
- [ ] Preparar capturas reales sin datos personales de clientes.
- [ ] Revisar contenido, edad, datos personales, acceso a funciones y permisos en Play Console.
- [ ] Realizar prueba cerrada de Play Console cuando corresponda al tipo/antigüedad de cuenta.
- [ ] Crear estrategia de migración desde APK independientes de desarrollo.

## Política y publicación
Documentación oficial:
- API objetivo: https://support.google.com/googleplay/android-developer/answer/11926878
- Firmas: https://support.google.com/googleplay/android-developer/answer/9842756
- Datos y privacidad: https://support.google.com/googleplay/android-developer/answer/10144311
- Recursos gráficos: https://support.google.com/googleplay/android-developer/answer/9866151

Google Play requiere icono 512×512, imagen promocional 1024×500 y capturas de la app. Deben representar la interfaz REAL. Falta preparar y revisar esos recursos.

**No afirmar que BELSIPERU emite boletas o facturas electrónicas ante SUNAT**: actualmente genera cotizaciones y proformas comerciales, no comprobantes electrónicos autorizados.
