# MasterMike

Proyecto Android base, sin dependencias externas, preparado para generar una APK de prueba. La pantalla inicial sirve como punto de partida para añadir las funciones de MasterMike.

## Requisitos

- JDK 17
- Android SDK con **Android API 35** instalado
- Conexión a internet en la primera compilación para que Gradle descargue el plugin de Android

El proyecto usa Android Gradle Plugin **8.7.3** y Gradle **8.9** a través del wrapper incluido.

## Compilar

Desde la raíz del repositorio:

```bash
# APK de desarrollo, firmada automáticamente con la clave de debug
./gradlew assembleDebug
```

El resultado queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para instalarla en un dispositivo o emulador conectado por ADB:

```bash
./gradlew installDebug
```

También se puede generar la variante de release:

```bash
./gradlew assembleRelease
```

La variante release se genera sin firmar. Para distribuirla hay que configurar una clave de firma en `app/build.gradle` o firmar el APK con `apksigner`.

En Windows, usa `gradlew.bat` en lugar de `./gradlew`.
