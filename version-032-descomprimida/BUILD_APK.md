# Compilar IGM JARDINES

## Compilación automática (GitHub Actions)

1. Sube el contenido completo de este proyecto a un repositorio GitHub.
2. Abre la pestaña **Actions** del repositorio.
3. Ejecuta **Build Android APK** con **Run workflow**, o realiza un `push` a `main`/`master`.
4. Cuando el trabajo termine correctamente, abre el resultado y descarga el artefacto `IGM-JARDINES-debug-apk`.
5. Extrae el ZIP del artefacto e instala `app-debug.apk` en un dispositivo Android compatible.

La APK de depuración no es una publicación firmada para Google Play. Para distribuir una versión final habrá que configurar una clave de firma privada y protegerla como secreto; nunca se debe incluir esa clave en el repositorio.

## Compilación local

Requisitos: JDK 17, Android SDK con plataforma 35 y Build Tools 35.0.0, y Gradle 8.9.

Desde la carpeta raíz del proyecto:

```bash
gradle --no-daemon assembleDebug
```

Salida esperada:

```text
app/build/outputs/apk/debug/app-debug.apk
```

La compilación debe completarse sin errores antes de considerar la APK instalable. Después hay que probar instalación, apertura, migraciones de Room, exportación/restauración de copias y permisos de notificaciones en un dispositivo real.
