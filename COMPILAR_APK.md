# Compilar APK de prueba de BELSIPERU

**Estado:** el APK no se ha generado ni probado. Este paquete es el proyecto fuente.

1. Instala Android Studio en una computadora Windows, macOS o Linux.
2. Descomprime el ZIP y abre la carpeta `BELSIPERU_App_Android` en Android Studio.
3. Espera la sincronización de Gradle. Instala el SDK Android 35 si te lo pide.
4. Ve a **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. El archivo de prueba debería generarse en `app/build/outputs/apk/debug/app-debug.apk` si la compilación termina correctamente.
6. Copia el APK al teléfono Android, ábrelo y autoriza la instalación desde esa fuente si Android lo solicita.
7. Prueba solamente con productos y clientes ficticios. No uses datos reales todavía: la protección del PIN y la base de datos no están listas para uso comercial.

**Nota:** Este proyecto todavía no trae Gradle Wrapper. Si Android Studio no puede sincronizarlo o detecta errores de compilación, será necesario corregirlos antes de obtener el APK.
