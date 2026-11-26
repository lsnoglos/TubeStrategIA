# TubeStrategIA
Aplicación Android nativa en español para investigación de mercado de YouTube. Versión 0.4.0 (piloto privado).

## Instalar
Android 8.0 o superior. En GitHub Actions, abrir la ejecución exitosa más reciente y descargar el artefacto TubeStrategIA-firmada. Extraer TubeStrategIA.apk e instalar. La firma permanente se verifica en cada compilación. La primera prueba debe realizarse con una cuenta autorizada en Google OAuth y TEST_EMAILS del backend.

## Compilar
JDK 17, Android SDK 35 y Gradle 8.9. El workflow configura TUBESTRATEGIA_BACKEND_URL con la URL del piloto; puede sustituirse con una variable de repositorio del mismo nombre. No incluir secretos de firma ni claves API en el código.

## Datos reales y almacenamiento
Inicio de sesión Google, consultas públicas mediante Apps Script, límite compartido de 100 llamadas search diarias y respaldo de términos y filtros mediante SQLite y Google Drive appDataFolder. La clave de YouTube permanece en propiedades del script, no en la APK. La URL del servicio es pública; el piloto verifica el token de Google y una lista de correos autorizados. Véase [sincronización y despliegue](docs/SINCRONIZACION.md).

## Interfaz 0.4.0
Paleta azul petróleo y cian, encabezados fucsia-azul, tarjetas de estadísticas diferenciadas, fechas locales y duraciones legibles. Indicador de carga indeterminado animado con fases reales de consulta y respaldo; mensajes visibles de error. Acceso con información sobre permisos y cuenta con última sincronización confirmada durante la sesión. No implica verificación oficial de Google.

## Alcance y limitaciones
Catálogo regional, búsqueda adaptativa, fichas de videos y canales e historial personal de consultas. Todavía no existe recolección histórica de estadísticas externas, índices derivados ni módulo de planificación. Se requieren pruebas reales de acceso, consultas y sincronización en dos dispositivos; antes de una distribución pública debe reforzarse la validación de sesiones.

## Privacidad
Cada cuenta mantiene un historial independiente. El usuario puede autorizar o pausar el respaldo en Drive. No se solicita acceso general a sus documentos ni se almacena la clave de YouTube en el teléfono. Aplican los [Términos de YouTube](https://www.youtube.com/t/terms) y la [Política de privacidad de Google](https://policies.google.com/privacy).
