# Fase 1 — inteligencia de mercado
La APK es la interfaz móvil; el recolector futuro debe operar en servidor, incluso con el teléfono apagado. El módulo de planes queda explícitamente fuera de esta fase.

## 0.1.0
- Consultas directas a YouTube Data API con clave personal en memoria, sin credenciales en el código.
- Catálogo mostPopular por región/categoría; no es ranking global de nichos ni demanda.
- Búsqueda por término y fecha, ampliación 7/30/90 días hasta 10 resultados; orden oficial.
- Fichas de canales y hasta 25 publicaciones recientes.
- Contadores públicos, enlaces a YouTube, estado y fecha de consulta, manejo de ausencia de datos.
- No se almacenan snapshots ni se calculan métricas derivadas sin aprobación.
- Time Machine informa explícitamente de la falta de historial.

## Próximos incrementos
1. Backend con secretos en servidor, autenticación, control de cuotas y consulta por lotes; aprobar caso analítico con YouTube.
2. Fuente autorizada de interés de búsqueda: Google Trends (acceso alfa) o proveedor legítimo. No sustituir demanda por vistas.
3. Recolector automático con cobertura explícita, registros UTC, caducidad y procedencia. Guardar exclusivamente datos y períodos autorizados.
4. Búsqueda adaptativa completa por frecuencia y edad; muestra de videos nuevos y pequeños, no solo populares. Clasificación por nicho, idioma y formato confirmado o desconocido.
5. Time Machine: acumulados vs diferencias netas, diario/semanal/mensual, fecha seleccionada, comparación por edad, cobertura y huecos. No imputar historial inexistente.
6. Radar: popularidad, crecimiento, aceleración, persistencia, competencia, reactivación y declive; separar demanda de oferta y nivel de confianza. Calibrar umbrales.
7. Viralidad: detectar puntos de cambio; comparar medianas de cohortes a igual edad; concentración top 1/top 3, replicabilidad, actividad posterior al pico, consolidación y estacionalidad.
8. Estimación financiera por escenarios, no ingresos reales de terceros; habilitar solo tras permisos. Alertas desde servidor.
9. Análisis de cinco referencias con fuentes autorizadas, sin copiar guiones; preparación para fase 2.

## Validación
- Buscar gatos, IA y frutiseries; verificar ausencia de resultados, cuota, clave inválida y ausencia de contadores.
- Diferenciar popularidad de demanda; no mostrar porcentajes de crecimiento sin observaciones.
- Probar que navegar durante una consulta no sustituya una pantalla ya abierta.
- Verificar Android 8+ y conectividad.
- Referencias: https://developers.google.com/youtube/terms/developer-policies y https://developers.google.com/youtube/terms/derived-metrics-policy
