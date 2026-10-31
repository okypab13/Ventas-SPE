# Compras SPE

App Android para llevar el control de las compras en SPE que se descuentan de la nómina.

- Foto al ticket: lee el **Total** y la **fecha** con reconocimiento de texto en el propio móvil (sin internet, sin claves).
- Periodo de nómina: del 21 del mes anterior al 20 del mes en curso.
- Revisión antes de guardar, edición y borrado de compras.
- Exportar el periodo en CSV (Excel en español) para compartir por correo, WhatsApp, Drive...

## Compilar el APK con GitHub

1. Crea un repositorio privado nuevo (por ejemplo `Compras-SPE`).
2. Sube el contenido de esta carpeta tal cual, incluida la carpeta oculta `.github`.
3. En la pestaña **Actions** se lanza solo el flujo «Compilar APK» (o pulsa *Run workflow*).
4. Al terminar, descarga el artefacto `ComprasSPE-apk`, descomprímelo e instala `app-debug.apk` en el móvil.

La firma va fijada en `app/debug.keystore`, así que las siguientes versiones se instalan encima sin perder las compras guardadas.
