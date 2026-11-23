# Política de privacidad — BORRADOR PARA REVISIÓN DEL TITULAR

**Producto:** BELSIPERU Gestión Comercial  
**Responsable de publicación y contacto:** PENDIENTE DE CONFIRMAR  
**Correo de privacidad/soporte:** PENDIENTE DE CONFIRMAR  
**Fecha:** 23 de noviembre de 2026

> Este documento es un borrador técnico. No debe publicarse como política definitiva sin la revisión del responsable y la comprobación efectiva de todos los SDK y flujos de datos.

## Información gestionada
La aplicación permite ingresar información de negocio, como nombre comercial, RUC, direcciones, contactos, datos de clientes, productos, precios, existencias, movimientos de inventario y cotizaciones. También puede usar imágenes de productos y logotipos aportados por el usuario.

## Almacenamiento
La información comercial se conserva en la base de datos privada de la instalación local. Por defecto no hay registro de cuenta en servidores de BELSIPERU ni sincronización automática entre dispositivos.

## Seguridad
El acceso puede protegerse por PIN, código de emergencia o biometría de Android. Se almacenan verificadores criptográficos, no el PIN en texto plano en la versión actual. Las copias de seguridad opcionales se cifran con una contraseña escogida por la persona usuaria.

## Servicios opcionales y terceros
- La consulta de RUC/DNI, si se configura, transmite el identificador buscado a la URL HTTPS del servicio que el usuario haya elegido.
- El escaneo utiliza componentes disponibles mediante Google Play Services.
- Cuando el usuario comparte por correo o mensajería, el PDF y sus datos se entregan a la aplicación externa elegida.
- Las bibliotecas incorporadas podrían procesar datos técnicos; se debe auditar el comportamiento real y completar la declaración de seguridad de datos de Google Play en consecuencia.

## Control de datos
La aplicación permite exportar informes y copias cifradas. Hay una función para eliminar datos de la instalación con confirmación mediante PIN. Las copias exportadas fuera de la aplicación deben eliminarse donde el usuario las haya guardado; no se borran automáticamente.

## Retención
Los datos locales permanecen hasta que el usuario los elimine o desinstale la aplicación, sujeto al comportamiento del sistema Android. Las copias exportadas tienen la retención que determine su propietario.

## Derechos y consultas
El titular de la política deberá indicar un canal válido de contacto para consultas sobre privacidad y tratamiento de información. Para Perú se debe considerar la legislación de protección de datos personales aplicable.

## Pendientes antes de publicación
1. Identificar titular, dirección de contacto y correo real.
2. Verificar escáner, Play Services, tráfico RUC/DNI y servicios externos.
3. Publicar una URL HTTPS accesible públicamente, no editable y sin bloqueo geográfico.
4. Adaptar este texto tras revisión jurídica y completar Data safety en Play Console.
