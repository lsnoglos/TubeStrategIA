# TubeStrategIA
Aplicación Android nativa en español para investigación de mercado de YouTube. Versión 0.5.0 (piloto privado).

## Instalar
Android 8.0 o superior. En GitHub Actions, abrir la ejecución exitosa más reciente y descargar el artefacto TubeStrategIA-firmada. Extraer TubeStrategIA.apk e instalar. La firma permanente se verifica en cada compilación. La primera prueba debe realizarse con una cuenta autorizada en Google OAuth y TEST_EMAILS del backend.

## Compilar
JDK 17, Android SDK 35 y Gradle 8.9. El workflow configura TUBESTRATEGIA_BACKEND_URL con la URL del piloto; puede sustituirse con una variable de repositorio del mismo nombre. No incluir secretos de firma ni claves API en el código.

## Datos reales y almacenamiento
Inicio de sesión Google, consultas públicas mediante Apps Script, límite compartido de 100 llamadas search diarias y respaldo de términos y filtros mediante SQLite y Google Drive appDataFolder. La clave de YouTube permanece en propiedades del script, no en la APK. La URL del servicio es pública; el piloto verifica el token de Google y una lista de correos autorizados. Véase [sincronización y despliegue](docs/SINCRONIZACION.md).

## Interfaz 0.4.0
Paleta azul petróleo y cian, encabezados fucsia-azul, tarjetas de estadísticas diferenciadas, fechas locales y duraciones legibles. Indicador de carga indeterminado animado con fases reales de consulta y respaldo; mensajes visibles de error. Acceso con información sobre permisos y cuenta con última sincronización confirmada durante la sesión. No implica verificación oficial de Google.

## Catálogo 0.4.1
Miniaturas oficiales de videos a la derecha del título, con carga asincrónica Glide, placeholder y apertura en YouTube. No se realizan consultas API adicionales para obtener las URLs de las imágenes. Caché de disco de miniaturas deshabilitada. Cuatro opciones de catálogo oficial y 31 temas predefinidos mediante el buscador existente; estos últimos consumen 1–3 llamadas search y no son categorías oficiales. El tema nacional busca el nombre del país elegido, sin verificar origen del creador. Streaming e IA son búsquedas temáticas, no verificaciones de emisión en vivo ni de generación por IA. No requiere actualizar Apps Script.

## Interfaz 0.5.0
Fondo claro, tarjetas compactas con tres contadores y tres acciones en filas. Inicio muestra Top 3 por vistas, likes y comentarios de la muestra mostPopular regional con fecha de consulta, no de todo YouTube ni actividad por período. Catálogo y Nichos mantienen resultados independientes en memoria durante la sesión, con filtros plegables. Sin enlace externo a Google Trends; información metodológica en Acerca de. Ficha del canal y estadísticas de video en ventanas emergentes. Actualizar contadores permite comparar dos lecturas originales durante la ventana; no se almacenan series históricas ni se calculan métricas derivadas. No se modifica el backend.

## Alcance y limitaciones
Catálogo regional, búsqueda adaptativa, fichas de videos y canales e historial personal de consultas. Todavía no existe recolección histórica de estadísticas externas, índices derivados ni módulo de planificación. Se requieren pruebas reales de acceso, consultas y sincronización en dos dispositivos; antes de una distribución pública debe reforzarse la validación de sesiones.

## Privacidad
Cada cuenta mantiene un historial independiente. El usuario puede autorizar o pausar el respaldo en Drive. No se solicita acceso general a sus documentos ni se almacena la clave de YouTube en el teléfono. Aplican los [Términos de YouTube](https://www.youtube.com/t/terms) y la [Política de privacidad de Google](https://policies.google.com/privacy).
