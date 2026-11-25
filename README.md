# TubeStrategIA
Aplicación Android nativa en español para investigación de mercado de YouTube. Versión inicial 0.1.0.

## Compilar
JDK 17, Android SDK 35 y Gradle 8.9. Desde la raíz: `gradle :app:assembleDebug`.
APK de pruebas: `app/build/outputs/apk/debug/app-debug.apk`. También se configura compilación en GitHub Actions, si el repositorio permite workflows.
Android 8.0 o superior.

## Datos reales
Crear proyecto en Google Cloud, habilitar YouTube Data API v3 y crear una clave restringida a esa API. Introducirla en el teléfono mediante "Clave API". La clave vive solo en memoria y no se incorpora al APK ni se registra en GitHub. Esta modalidad es únicamente de prueba personal; para distribución se requiere un backend que custodie los secretos.

## Alcance
Catálogo regional, búsqueda por término con ampliación temporal, estadísticas públicas y fichas de canales. No hay historial retroactivo, almacenamiento de estadísticas, índices derivados ni estimaciones financieras en esta versión. El módulo de planificación no está incluido. Consulte [Fase 1](docs/FASE-1.md).

## Privacidad
No se guardan credenciales, estadísticas ni información de usuario en disco; no hay servidor propio ni telemetría. Las consultas van por HTTPS a Google. Aplican los [Términos de YouTube](https://www.youtube.com/t/terms) y la [Política de privacidad de Google](https://policies.google.com/privacy).
