# BELSIPERU Android — desarrollo v0.3

Proyecto fuente Android. **No es un APK instalable ni una versión final probada.**

## Avances de esta entrega
- El generador de PDF Android incluye el logotipo extraído de la proforma de referencia.
- Mantiene selección de diseños Moderno, Corporativo y Prémium.
- Mantiene A4 vertical/horizontal, A5 vertical/horizontal y ticket 80 mm.
- Integra fotografía de producto (si se seleccionó en el catálogo), descuentos por porcentaje y monto, IGV opcional y moneda PEN/USD.
- Añade a PDFs no compactos condición de pago, cuentas Interbank y condiciones de entrega.
- Se incluyen tres ejemplos de diseño en PDF para revisión.

## Funciones previas existentes
- Catálogo y clientes SQLite offline, PIN prototipo, historial, Kardex básico, alertas de mínimo.
- Cotizaciones con **una línea de producto** y confirmación de venta.

## Trabajo aún necesario antes de uso comercial
- Múltiples productos por cotización, impresión multipágina, salto de página correcto, texto largo, formato ticket optimizado.
- Integrar completamente los diseños visuales de ejemplo en el generador nativo: la app aún genera versiones simplificadas.
- Gestión completa de proveedores, compras, órdenes, Excel, rentabilidad, fotos desde cámara y búsqueda RUC/DNI.
- PIN seguro con hash y recuperación, biometría, bloqueo a los 5 minutos, cifrado de datos y backups.
- Evitar stock negativo, gestión de devoluciones, impuestos y cálculos robustos, pruebas en dispositivo.
- Compilar y probar con Android Studio/SDK; este entorno no tiene SDK de Android.

**Advertencia:** no guardar información comercial real o sensible en este prototipo.
