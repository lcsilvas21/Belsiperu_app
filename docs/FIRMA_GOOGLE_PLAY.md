# Firma de publicación y protección de claves (BELSIPERU)

**Solo el titular de la cuenta Google Play debe ejecutar estos pasos. No envíes las contraseñas ni el archivo .jks por chat, correo o al repositorio.**

## 1. Generar una clave de subida privada

Con JDK 17 instalado en un equipo propio:

```bash
keytool -genkeypair -v -storetype PKCS12 \
  -keystore belsiperu-upload.jks \
  -alias belsiperu-upload -keyalg RSA -keysize 4096 -validity 10000
```

Guardar el archivo en un gestor seguro, con copia desconectada, y documentar el alias/contraseñas para el administrador. No usar la clave de depuración.

## 2. Configurar GitHub Actions Secrets del repositorio

Crear secretos de **Actions** (no variables públicas):

- `BELSI_UPLOAD_KEYSTORE_BASE64` contiene el almacén de claves convertido a base64 en **una sola línea**.
- `BELSI_UPLOAD_KEY_ALIAS` alias usado con `keytool`.
- `BELSI_UPLOAD_STORE_PASSWORD` contraseña del almacén.
- `BELSI_UPLOAD_KEY_PASSWORD` contraseña de la clave.

El archivo temporal se reconstruye solo durante la compilación manual. No debe guardarse como artefacto.

En Linux:
```bash
base64 -w 0 belsiperu-upload.jks
```
En macOS:
```bash
base64 < belsiperu-upload.jks | tr -d '\n'
```

**No pegues la cadena base64 en issues, commits o mensajes.**

## 3. Crear el AAB firmado

En GitHub → Actions → **Google Play - AAB firmado (manual)** → Run workflow. Descargar el artefacto AAB firmado. El workflow no publica ni despliega automáticamente.

## 4. En Play Console

1. Confirmar el ID final **`pe.belsiperu.gestioncomercial`** antes de crear la aplicación.
2. Configurar Play App Signing y registrarse con la clave de subida.
3. Completar fichas, política de privacidad HTTPS, seguridad de datos y clasificación.
4. Subir el AAB a un canal de prueba interna/cerrada, no a Producción.
5. Probar la instalación mediante Google Play y que las actualizaciones conserven los datos.
6. Confirmar pruebas obligatorias según tipo y antigüedad de la cuenta.
7. Enviar a revisión de producción cuando corresponda.

## Precauciones

- Producir un nuevo AAB incrementando `versionCode` en cada actualización.
- Si la clave de subida se pierde, seguir el procedimiento de Play App Signing para restablecerla. No recrear con credenciales distintas sin comprobar el proceso.
- No instalar la edición pública encima de las APK de pruebas si tienen diferentes package IDs. Exportar/importar los productos o utilizar respaldo compatible, con pruebas de integridad.
- No usar el AAB **SIN FIRMA** de la compilación de CI para publicación.

Referencia: https://support.google.com/googleplay/android-developer/answer/9842756
