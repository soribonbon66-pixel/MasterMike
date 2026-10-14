# Michel Mastering PRO

**App Android de Mastering Automatizado**

Automatiza el proceso de mastering de audio con análisis EBU R128, ecualización, compresión, limitación y normalización de loudness. Procesamiento 100% offline.

## Características

- 📊 **Análisis completo EBU R128**: Integrated LUFS, True Peak dBTP, LRA
- 🎚️ **Análisis dinámico**: RMS, Crest Factor, Dynamic Range, detección de clipping
- 📈 **Análisis espectral**: FFT 4096, detección de problemas de graves, medios y aire
- 🔊 **Análisis estéreo**: Correlación, ancho estéreo, mono compatibility
- 🎵 **Análisis tonal**: BPM estimado (autocorrelación), tonalidad (chromagram)
- 🎛️ **Sugerencias inteligentes**: EQ, compresor, limiter generados automáticamente del análisis
- ⚡ **Cadena de procesamiento**: Gain → EQ → Compressor → Limiter → Normalization
- 🎧 **Escucha A/B**: Comparación instantánea original vs masterizado
- 📋 **Análisis comparativo**: Antes/después con validación EBU R128
- 💾 **Exportación WAV**: 24-bit PCM, guardado en Music/Michel/
- 🔗 **Compartir**: Envía el WAV masterizado

## Arquitectura

```
MVVM + StateFlow
├── UI (Jetpack Compose + Material3)
│   ├── LoadScreen → Carga de audio
│   ├── AnalysisScreen → Análisis + Ajustes sugeridos editables
│   └── ResultScreen → Comparativa + A/B + Exportar
├── ViewModel
│   └── MasteringViewModel → State management
├── Audio
│   ├── AudioLoader → Decodificación (MediaExtractor + MediaCodec)
│   ├── AudioAnalyzer → Análisis completo
│   ├── SuggestionEngine → Reglas de sugerencia
│   ├── AudioProcessor → Cadena DSP
│   └── WavWriter → Exportación WAV
└── DSP (implementación propia en Kotlin)
    ├── Eq → Biquad filters (RBJ Cookbook)
    ├── Compressor → Feed-forward con stereo linking
    ├── Limiter → Brickwall con lookahead
    ├── LoudnessMeter → EBU R128 (K-weighting + gating)
    └── SpectrumAnalyzer → FFT Cooley-Tukey
```

## Stack Técnico

- **Lenguaje**: Kotlin 100%
- **UI**: Jetpack Compose + Material3
- **Arquitectura**: MVVM + ViewModel + StateFlow
- **Audio Decode**: MediaExtractor + MediaCodec → PCM 32-bit float
- **DSP**: Implementación propia en Kotlin (sin NDK)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Gradle**: 8.4 + AGP 8.2.0 + Kotlin 1.9.10

## Compilación

### Local

1. Abre el proyecto en Android Studio Hedgehog o superior
2. Sincroniza Gradle
3. Run en dispositivo o emulador

### Línea de comandos

```bash
# Generar Gradle Wrapper (primera vez)
gradle wrapper --gradle-version 8.4

# Compilar APK debug
./gradlew assembleDebug

# APK disponible en:
# app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

El repositorio incluye un workflow automático en `.github/workflows/build.yml` que:

1. Se dispara en cada push a `main`/`master`
2. Compila el APK debug
3. Sube el APK como artifact descargable

Para activarlo:

```bash
git init
git add .
git commit -m "Initial commit: Michel Mastering PRO"
git remote add origin https://github.com/TU_USUARIO/MichelMasteringPRO.git
git push -u origin main
```

Luego ve a **Actions** en tu repositorio de GitHub para descargar el APK.

## Estructura del Proyecto

```
MichelMasteringPRO/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/michel/masteringpro/
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── MichelApp.kt
│       │   │   ├── AnalysisScreen.kt
│       │   │   ├── ResultScreen.kt
│       │   │   └── theme/
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   ├── viewmodel/
│       │   │   └── MasteringViewModel.kt
│       │   ├── audio/
│       │   │   ├── AudioLoader.kt
│       │   │   ├── AudioAnalyzer.kt
│       │   │   ├── SuggestionEngine.kt
│       │   │   ├── AudioProcessor.kt
│       │   │   └── WavWriter.kt
│       │   └── dsp/
│       │       ├── Eq.kt
│       │       ├── Compressor.kt
│       │       ├── Limiter.kt
│       │       ├── LoudnessMeter.kt
│       │       └── SpectrumAnalyzer.kt
│       └── res/
│           ├── drawable/
│           ├── mipmap-anydpi-v26/
│           ├── values/
│           └── xml/
├── build.gradle.kts (root)
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── .github/workflows/
│   └── build.yml
└── README.md
```

## Reglas de Sugerencia

El motor de sugerencias analiza el track y propone ajustes basados en:

| Condición | Acción |
|-----------|--------|
| LUFS > -12 | Gain = -14 - LUFS |
| LUFS < -18 | Gain = -14 - LUFS |
| TruePeak > -1.0 dBTP | Limiter ON, ceiling -1.0dB |
| Clipping > 0% | Limiter obligatorio |
| Exceso 200-400Hz | EQ Bell -2dB @300Hz Q=1.2 |
| Exceso 2-5kHz | EQ -1.5dB @3.5kHz |
| Falta aire >12kHz | High Shelf +1.5dB @12kHz |
| LRA > 9 LU | Compresor 1.5:1, threshold -18dB |
| Correlación < 0.3 | Aviso problema de fase |

## Licencia

MIT License
